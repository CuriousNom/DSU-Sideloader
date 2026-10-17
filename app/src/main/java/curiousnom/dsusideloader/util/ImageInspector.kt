package curiousnom.dsusideloader.util

import java.io.InputStream

object ImageInspector {

    enum class ImageFormat(val displayName: String) {
        SPARSE("Android Sparse Image"),
        EROFS("EROFS (Read-Only FS)"),
        EXT4("ext4 Filesystem"),
        GZIP("GZIP Archive"),
        XZ("XZ Archive"),
        ZIP("ZIP Package"),
        RAW("Raw Image"),
        UNKNOWN("Image"),
    }

    fun inspect(input: InputStream): ImageFormat {
        val header = ByteArray(2048)
        var totalRead = 0
        try {
            while (totalRead < header.size) {
                val read = input.read(header, totalRead, header.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
        } catch (e: Exception) {
            return ImageFormat.UNKNOWN
        }

        if (totalRead < 4) return ImageFormat.UNKNOWN

        // Check Android Sparse image (0xED26FF3A -> 3A FF 26 ED in little-endian)
        if (header[0] == 0x3A.toByte() &&
            header[1] == 0xFF.toByte() &&
            header[2] == 0x26.toByte() &&
            header[3] == 0xED.toByte()
        ) {
            return ImageFormat.SPARSE
        }

        // Check GZIP (0x1F 0x8B)
        if (header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
            return ImageFormat.GZIP
        }

        // Check XZ (0xFD '7' 'z' 'X' 'Z' 0x00)
        if (totalRead >= 6 &&
            header[0] == 0xFD.toByte() &&
            header[1] == '7'.code.toByte() &&
            header[2] == 'z'.code.toByte() &&
            header[3] == 'X'.code.toByte() &&
            header[4] == 'Z'.code.toByte() &&
            header[5] == 0x00.toByte()
        ) {
            return ImageFormat.XZ
        }

        // Check ZIP ('P' 'K' 0x03 0x04)
        if (header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
        ) {
            return ImageFormat.ZIP
        }

        // Check EROFS (offset 1024: 0xE2F569E5 -> E5 69 F5 E2 in little-endian)
        if (totalRead >= 1028) {
            if (header[1024] == 0xE5.toByte() &&
                header[1025] == 0x69.toByte() &&
                header[1026] == 0xF5.toByte() &&
                header[1027] == 0xE2.toByte()
            ) {
                return ImageFormat.EROFS
            }
        }

        // Check ext4 (offset 1080: 0xEF53 -> 53 EF in little-endian)
        if (totalRead >= 1082) {
            if (header[1080] == 0x53.toByte() &&
                header[1081] == 0xEF.toByte()
            ) {
                return ImageFormat.EXT4
            }
        }

        return ImageFormat.RAW
    }
}
