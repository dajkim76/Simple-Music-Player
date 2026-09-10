package com.simplemobiletools.musicplayer.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getFilenameExtension
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getTempFile
import com.simplemobiletools.musicplayer.models.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.SupportedFileFormat
import org.jaudiotagger.audio.generic.Utils
import org.jaudiotagger.audio.ogg.OggVorbisCommentTagCreator
import org.jaudiotagger.audio.ogg.util.OggPage
import org.jaudiotagger.audio.ogg.util.OggPageHeader
import org.jaudiotagger.audio.opus.OpusHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class TagHelper(private val activity: BaseSimpleActivity) {

    init {
        TagOptionSingleton.getInstance().isAndroid = true
    }

    companion object {
        private const val TEMP_FOLDER = "music"

        // Editing tags in WMA and WAV files are flaky so we exclude them
        private val EXCLUDED_EXTENSIONS = listOf("wma", "wav")
        private val SUPPORTED_EXTENSIONS = SupportedFileFormat.values().map { it.filesuffix }.filter { it.isNotEmpty() && it !in EXCLUDED_EXTENSIONS }

        fun isEditTagSupported(track: Track): Boolean {
            return SUPPORTED_EXTENSIONS.any { it.equals(track.path.getFilenameExtension(), ignoreCase = true) }
        }
    }

    fun isEditTagSupported(track: Track): Boolean = Companion.isEditTagSupported(track)

    fun writeTag(track: Track, newArtist: String, newTitle: String, newAlbum: String) {
        if (isEditTagSupported(track)) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId)
            val temp = activity.getTempFile(TEMP_FOLDER, track.path.getFilenameFromPath()) ?: return
            try {
                val inputStream = activity.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input stream for ${track.path}")
                inputStream.use { stream ->
                    temp.outputStream().use { out ->
                        stream.copyTo(out)
                    }
                }

                val extension = track.path.getFilenameExtension()
                val audioFile = AudioFileIO.read(temp)
                val tag = audioFile.tag ?: createTag(extension).also { audioFile.tag = it }
                tag.setField(FieldKey.TITLE, newTitle)
                tag.setField(FieldKey.ARTIST, newArtist)
                tag.setField(FieldKey.ALBUM, newAlbum)

                if (extension.equals(SupportedFileFormat.OPUS.filesuffix, ignoreCase = true)) {
                    writeOpusTagStreaming(temp, tag)
                } else {
                    audioFile.commit()
                }

                val outputStream = activity.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Cannot open output stream for ${track.path}")
                outputStream.use { stream ->
                    temp.inputStream().use { streamIn ->
                        streamIn.copyTo(stream)
                    }
                }

                updateContentResolver(track, newTitle, newArtist, newAlbum)
            } finally {
                temp.delete()
            }
        }
    }

    private fun writeOpusTagStreaming(file: File, tag: Tag) {
        val targetFile = File(file.parentFile, "${file.name}.tmp")
        try {
            RandomAccessFile(file, "r").use { sourceRaf ->
                RandomAccessFile(targetFile, "rw").use { targetRaf ->
                    val targetChannel = targetRaf.channel
                    val tc = OggVorbisCommentTagCreator(ByteArray(0), OpusHeader.TAGS_CAPTURE_PATTERN_AS_BYTES, false)
                    val tagBuffer = tc.convert(tag)

                    // 1. 첫 번째 페이지 (OpusHead identification header)
                    val firstPage = readPage(sourceRaf)
                    writePage(targetChannel, firstPage)

                    // 2. 기존 OpusTags 페이지 건너뛰기
                    readPage(sourceRaf) // 기존 태그 첫 페이지 건너뜀
                    var firstAudioPage: OggPage? = null
                    // Ogg 페이지 기본 헤더(Ogg Page Header)의 최소 고정 크기(27 바이트)
                    while (sourceRaf.length() - sourceRaf.filePointer >= 27) {
                        val page = try {
                            readPage(sourceRaf)
                        } catch (e: Exception) {
                            break
                        }
                        if (page.header.isContinuedPage) {
                            continue
                        } else {
                            firstAudioPage = page
                            break
                        }
                    }

                    if (firstAudioPage == null) {
                        throw IOException("Failed to find audio stream in Opus file: ${file.name}")
                    }

                    // 3. 새 태그 페이지 쓰기
                    val serialNumber = firstPage.header.serialNumber
                    var sequenceNo = 1
                    var isContinued = false

                    while (tagBuffer.hasRemaining()) {
                        val chunkSize = minOf(tagBuffer.remaining(), 65025)
                        val header = OggPageHeader.createCommentHeader(chunkSize, isContinued, serialNumber, sequenceNo++)
                        val slice = tagBuffer.slice()
                        (slice as Buffer).limit(chunkSize)
                        writePage(targetChannel, OggPage(header, slice))
                        Utils.skip(tagBuffer, chunkSize)
                        isContinued = true
                    }

                    // 4. 나머지 모든 오디오 페이지 스트리밍 복사 및 sequenceNo 갱신
                    firstAudioPage.setSequenceNo(sequenceNo++)
                    writePage(targetChannel, firstAudioPage)
                    if (firstAudioPage.header.isLastPage) {
                        return
                    }
                    while (sourceRaf.length() - sourceRaf.filePointer >= 27) {
                        val audioPage = try {
                            readPage(sourceRaf)
                        } catch (e: Exception) {
                            // 파일 끝부분의 쓰레기 데이터나 패딩은 무시
                            break
                        }
                        audioPage.setSequenceNo(sequenceNo++)
                        writePage(targetChannel, audioPage)
                        if (audioPage.header.isLastPage) {
                            break
                        }
                    }
                }
            }
            if (!targetFile.renameTo(file)) {
                targetFile.copyTo(file, overwrite = true)
                targetFile.delete()
            }
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    private fun readPage(raf: RandomAccessFile): OggPage {
        val header = OggPageHeader.read(raf)
        val content = ByteArray(header.pageLength)
        raf.readFully(content)
        return OggPage(header, ByteBuffer.wrap(content))
    }

    private fun writePage(channel: FileChannel, page: OggPage) {
        val buf = ByteBuffer.allocate(page.size())
        page.write(buf)
        buf.rewind()
        channel.write(buf)
    }

    private fun createTag(extension: String): Tag {
        return when (extension) {
            SupportedFileFormat.OGG.filesuffix,
            SupportedFileFormat.OPUS.filesuffix -> VorbisCommentTag()

            SupportedFileFormat.M4A.filesuffix -> Mp4Tag()
            SupportedFileFormat.FLAC.filesuffix -> FlacTag()
            else -> ID3v24Tag()
        }
    }

    private fun updateContentResolver(track: Track, newTitle: String, newArtist: String, newAlbum: String) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val where = "${MediaStore.Audio.Media._ID} = ?"
        val args = arrayOf(track.mediaStoreId.toString())

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
        }
        activity.contentResolver.update(uri, values, where, args)
    }
}
