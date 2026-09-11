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
import org.mp4parser.IsoFile
import org.mp4parser.PropertyBoxParserImpl
import java.util.Properties
import org.mp4parser.boxes.apple.AppleAlbumBox
import org.mp4parser.boxes.apple.AppleArtistBox
import org.mp4parser.boxes.apple.AppleItemListBox
import org.mp4parser.boxes.apple.AppleNameBox
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox
import org.mp4parser.boxes.iso14496.part12.MetaBox
import org.mp4parser.boxes.iso14496.part12.MovieBox
import org.mp4parser.boxes.iso14496.part12.TrackBox
import org.mp4parser.boxes.iso14496.part12.UserDataBox
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
                } else if (extension.equals(SupportedFileFormat.M4A.filesuffix, ignoreCase = true) ||
                    extension.equals(SupportedFileFormat.M4B.filesuffix, ignoreCase = true) ||
                    extension.equals(SupportedFileFormat.M4P.filesuffix, ignoreCase = true) ||
                    extension.equals(SupportedFileFormat.MP4.filesuffix, ignoreCase = true)
                ) {
                    writeMp4TagStreaming(temp, newArtist, newTitle, newAlbum)
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

    private fun createMp4BoxParser(): PropertyBoxParserImpl {
        val props = Properties()
        props.setProperty("ftyp", "org.mp4parser.boxes.iso14496.part12.FileTypeBox")
        props.setProperty("moov", "org.mp4parser.boxes.iso14496.part12.MovieBox")
        props.setProperty("mvhd", "org.mp4parser.boxes.iso14496.part12.MovieHeaderBox")
        props.setProperty("trak", "org.mp4parser.boxes.iso14496.part12.TrackBox")
        props.setProperty("tkhd", "org.mp4parser.boxes.iso14496.part12.TrackHeaderBox")
        props.setProperty("mdia", "org.mp4parser.boxes.iso14496.part12.MediaBox")
        props.setProperty("mdhd", "org.mp4parser.boxes.iso14496.part12.MediaHeaderBox")
        props.setProperty("hdlr", "org.mp4parser.boxes.iso14496.part12.HandlerBox")
        props.setProperty("minf", "org.mp4parser.boxes.iso14496.part12.MediaInformationBox")
        props.setProperty("smhd", "org.mp4parser.boxes.iso14496.part12.SoundMediaHeaderBox")
        props.setProperty("vmhd", "org.mp4parser.boxes.iso14496.part12.VideoMediaHeaderBox")
        props.setProperty("dinf", "org.mp4parser.boxes.iso14496.part12.DataInformationBox")
        props.setProperty("dref", "org.mp4parser.boxes.iso14496.part12.DataReferenceBox")
        props.setProperty("stbl", "org.mp4parser.boxes.iso14496.part12.SampleTableBox")
        props.setProperty("stsd", "org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox")
        props.setProperty("stts", "org.mp4parser.boxes.iso14496.part12.TimeToSampleBox")
        props.setProperty("stsz", "org.mp4parser.boxes.iso14496.part12.SampleSizeBox")
        props.setProperty("stsc", "org.mp4parser.boxes.iso14496.part12.SampleToChunkBox")
        props.setProperty("stco", "org.mp4parser.boxes.iso14496.part12.StaticChunkOffsetBox")
        props.setProperty("co64", "org.mp4parser.boxes.iso14496.part12.ChunkOffset64BitBox")
        props.setProperty("udta", "org.mp4parser.boxes.iso14496.part12.UserDataBox")
        props.setProperty("meta", "org.mp4parser.boxes.iso14496.part12.MetaBox")
        props.setProperty("ilst", "org.mp4parser.boxes.apple.AppleItemListBox")
        props.setProperty("meta-ilst", "org.mp4parser.boxes.apple.AppleItemListBox")
        props.setProperty("\u00A9nam", "org.mp4parser.boxes.apple.AppleNameBox")
        props.setProperty("\u00A9ART", "org.mp4parser.boxes.apple.AppleArtistBox")
        props.setProperty("aART", "org.mp4parser.boxes.apple.AppleArtist2Box")
        props.setProperty("\u00A9alb", "org.mp4parser.boxes.apple.AppleAlbumBox")
        props.setProperty("mdat", "org.mp4parser.boxes.iso14496.part12.MediaDataBox")
        props.setProperty("free", "org.mp4parser.boxes.iso14496.part12.FreeBox")
        props.setProperty("skip", "org.mp4parser.boxes.iso14496.part12.FreeSpaceBox")
        props.setProperty("default", "org.mp4parser.boxes.iso14496.part12.MediaDataBox")
        return PropertyBoxParserImpl(props)
    }

    private fun writeMp4TagStreaming(file: File, newArtist: String, newTitle: String, newAlbum: String) {
        val targetFile = File(file.parentFile, "${file.name}.mp4tmp")
        try {
            RandomAccessFile(file, "r").channel.use { fileChannel ->
                IsoFile(fileChannel, createMp4BoxParser()).use { isoFile ->
                val moov = isoFile.movieBox ?: throw IOException("MovieBox (moov) not found in ${file.name}")
                val oldMoovSize = moov.size

                // 1. UserDataBox (udta) 찾기 또는 생성
                var udta = moov.getBoxes(UserDataBox::class.java).firstOrNull()
                if (udta == null) {
                    udta = UserDataBox()
                    moov.addBox(udta)
                }

                // 2. MetaBox (meta) 찾기 또는 생성
                var meta = udta.getBoxes(MetaBox::class.java).firstOrNull()
                if (meta == null) {
                    meta = MetaBox()
                    udta.addBox(meta)
                }

                // 3. AppleItemListBox (ilst) 찾기 또는 생성
                var ilst = meta.getBoxes(AppleItemListBox::class.java).firstOrNull()
                if (ilst == null) {
                    ilst = AppleItemListBox()
                    meta.addBox(ilst)
                }

                // 4. ilst 내 태그 교체 (©nam, ©ART, ©alb)
                val currentBoxes = ilst.boxes.filter { box ->
                    box.type != "©nam" && box.type != "©ART" && box.type != "©alb"
                }.toMutableList()

                if (newTitle.isNotEmpty()) {
                    val nameBox = AppleNameBox()
                    nameBox.value = newTitle
                    currentBoxes.add(nameBox)
                }

                if (newArtist.isNotEmpty()) {
                    val artistBox = AppleArtistBox()
                    artistBox.value = newArtist
                    currentBoxes.add(artistBox)
                }

                if (newAlbum.isNotEmpty()) {
                    val albumBox = AppleAlbumBox()
                    albumBox.value = newAlbum
                    currentBoxes.add(albumBox)
                }

                ilst.boxes = currentBoxes

                // 5. moov 크기 변화 계산 및 stco / co64 오프셋 보정
                val newMoovSize = moov.size
                val sizeDelta = newMoovSize - oldMoovSize

                if (sizeDelta != 0L) {
                    val trackBoxes = moov.getBoxes(TrackBox::class.java)
                    for (trackBox in trackBoxes) {
                        val stbl = trackBox.sampleTableBox ?: continue
                        val chunkOffsetBox = stbl.chunkOffsetBox ?: continue
                        val offsets = chunkOffsetBox.chunkOffsets
                        for (i in offsets.indices) {
                            offsets[i] += sizeDelta
                        }
                        chunkOffsetBox.chunkOffsets = offsets
                    }
                }

                // 6. 대상 파일에 작성
                targetFile.outputStream().channel.use { targetChannel ->
                    isoFile.getBox(targetChannel)
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

            SupportedFileFormat.M4A.filesuffix,
            SupportedFileFormat.M4B.filesuffix,
            SupportedFileFormat.M4P.filesuffix,
            SupportedFileFormat.MP4.filesuffix -> Mp4Tag()
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
