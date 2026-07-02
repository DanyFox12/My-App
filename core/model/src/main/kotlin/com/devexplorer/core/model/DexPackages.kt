package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * One node of the DEX package tree. [classCount]/[methodRefs] are the counts
 * *directly in* this package; the `total*` properties add every descendant —
 * that's what answers "which library is eating my method budget?".
 */
@Serializable
data class DexPackageNode(
    /** Single segment name ("com", "example"); the root's name is "". */
    val name: String,
    /** Classes defined directly in this package (across all DEX files). */
    val classCount: Int,
    /** Method references whose declaring type sits directly in this package. */
    val methodRefs: Int,
    val children: List<DexPackageNode>,
) {
    val totalClasses: Int get() = classCount + children.sumOf { it.totalClasses }
    val totalMethodRefs: Int get() = methodRefs + children.sumOf { it.totalMethodRefs }
}

/**
 * Walks whole `classes*.dex` files (not just the header, as [Dex] does) to
 * aggregate a per-package view: which packages define classes and where the
 * method *references* — the 64K-budget currency — point. External packages the
 * app only calls into (e.g. `android.*`) appear with zero defined classes.
 *
 * Reading is bounds-checked and **fail-soft** per file: one malformed DEX is
 * skipped rather than sinking the whole tree.
 */
object DexPackages {

    private const val HEADER_SIZE = 0x70
    private const val OFF_STRING_IDS_SIZE = 0x38
    private const val OFF_STRING_IDS_OFF = 0x3C
    private const val OFF_TYPE_IDS_SIZE = 0x40
    private const val OFF_TYPE_IDS_OFF = 0x44
    private const val OFF_METHOD_IDS_SIZE = 0x58
    private const val OFF_METHOD_IDS_OFF = 0x5C
    private const val OFF_CLASS_DEFS_SIZE = 0x60
    private const val OFF_CLASS_DEFS_OFF = 0x64
    private const val CLASS_DEF_BYTES = 32
    private const val METHOD_ID_BYTES = 8

    /** Build the merged package tree across [dexFiles]; null when none parse. */
    fun parse(dexFiles: List<ByteArray>): DexPackageNode? = parse(dexFiles.asSequence())

    /**
     * Sequence variant so callers can stream one DEX at a time (multidex apps
     * can carry hundreds of MB of DEX; only one file's bytes are alive at once).
     */
    fun parse(dexFiles: Sequence<ByteArray>): DexPackageNode? {
        val classes = HashMap<String, Int>()
        val methods = HashMap<String, Int>()
        var parsedAny = false
        for (bytes in dexFiles) {
            if (runCatching { accumulate(bytes, classes, methods) }.getOrDefault(false)) {
                parsedAny = true
            }
        }
        if (!parsedAny) return null
        return buildTree(classes, methods)
    }

    /** Parse one DEX and add its per-package counts into the accumulators. */
    private fun accumulate(
        bytes: ByteArray,
        classes: MutableMap<String, Int>,
        methods: MutableMap<String, Int>,
    ): Boolean {
        if (bytes.size < HEADER_SIZE || Dex.parseHeader("", bytes.copyOf(HEADER_SIZE)) == null) return false

        val stringIdsSize = u32(bytes, OFF_STRING_IDS_SIZE)
        val stringIdsOff = u32(bytes, OFF_STRING_IDS_OFF)
        val typeIdsSize = u32(bytes, OFF_TYPE_IDS_SIZE)
        val typeIdsOff = u32(bytes, OFF_TYPE_IDS_OFF)
        val methodIdsSize = u32(bytes, OFF_METHOD_IDS_SIZE)
        val methodIdsOff = u32(bytes, OFF_METHOD_IDS_OFF)
        val classDefsSize = u32(bytes, OFF_CLASS_DEFS_SIZE)
        val classDefsOff = u32(bytes, OFF_CLASS_DEFS_OFF)

        // Resolve type index -> "com.example" package (null for arrays/primitives),
        // memoized because method_ids repeat declaring types heavily.
        val packageOfType = HashMap<Int, String?>()
        fun typePackage(typeIdx: Int): String? = packageOfType.getOrPut(typeIdx) {
            if (typeIdx < 0 || typeIdx >= typeIdsSize) return@getOrPut null
            val stringIdx = u32(bytes, typeIdsOff + typeIdx * 4)
            if (stringIdx < 0 || stringIdx >= stringIdsSize) return@getOrPut null
            val descriptor = readString(bytes, u32(bytes, stringIdsOff + stringIdx * 4)) ?: return@getOrPut null
            descriptorToPackage(descriptor)
        }

        // Count into per-file maps first: a DEX whose header validates but whose
        // tables run out of bounds throws mid-loop, and "fail-soft per file"
        // means none of its partial counts may leak into the shared totals.
        val fileClasses = HashMap<String, Int>()
        val fileMethods = HashMap<String, Int>()
        for (i in 0 until classDefsSize) {
            val pkg = typePackage(u32(bytes, classDefsOff + i * CLASS_DEF_BYTES)) ?: continue
            fileClasses[pkg] = (fileClasses[pkg] ?: 0) + 1
        }
        for (i in 0 until methodIdsSize) {
            val pkg = typePackage(u16(bytes, methodIdsOff + i * METHOD_ID_BYTES)) ?: continue
            fileMethods[pkg] = (fileMethods[pkg] ?: 0) + 1
        }
        for ((pkg, count) in fileClasses) classes[pkg] = (classes[pkg] ?: 0) + count
        for ((pkg, count) in fileMethods) methods[pkg] = (methods[pkg] ?: 0) + count
        return true
    }

    /** "Lcom/example/Foo;" -> "com.example"; top-level classes map to ""; else null. */
    internal fun descriptorToPackage(descriptor: String): String? {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return null
        val body = descriptor.substring(1, descriptor.length - 1)
        val lastSlash = body.lastIndexOf('/')
        return if (lastSlash < 0) "" else body.substring(0, lastSlash).replace('/', '.')
    }

    /**
     * Read a string_data_item: a ULEB128 UTF-16 length we skip, then MUTF-8
     * bytes up to the NUL terminator. Package/class names are what we're after,
     * so plain UTF-8 decoding of the byte run is accurate enough.
     */
    private fun readString(bytes: ByteArray, offset: Int): String? {
        var p = offset
        if (p < 0 || p >= bytes.size) return null
        // Skip the ULEB128 utf16_size prefix.
        while (p < bytes.size && (bytes[p].toInt() and 0x80) != 0) p++
        p++
        if (p >= bytes.size) return null
        var end = p
        while (end < bytes.size && bytes[end].toInt() != 0) end++
        return String(bytes, p, end - p, Charsets.UTF_8)
    }

    private fun buildTree(classes: Map<String, Int>, methods: Map<String, Int>): DexPackageNode {
        class Mutable(val name: String) {
            var classCount = 0
            var methodRefs = 0
            val children = LinkedHashMap<String, Mutable>()
            fun toNode(): DexPackageNode = DexPackageNode(
                name = name,
                classCount = classCount,
                methodRefs = methodRefs,
                children = children.values.map { it.toNode() }
                    .sortedByDescending { it.totalMethodRefs },
            )
        }

        val root = Mutable("")
        fun nodeFor(pkg: String): Mutable {
            if (pkg.isEmpty()) return root
            var node = root
            for (segment in pkg.split('.')) {
                node = node.children.getOrPut(segment) { Mutable(segment) }
            }
            return node
        }
        for ((pkg, count) in classes) nodeFor(pkg).classCount = count
        for ((pkg, count) in methods) nodeFor(pkg).methodRefs = count
        return root.toNode()
    }

    private fun u8(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u16(b: ByteArray, p: Int): Int = u8(b, p) or (u8(b, p + 1) shl 8)
    private fun u32(b: ByteArray, p: Int): Int =
        u16(b, p) or (u16(b, p + 2) shl 16)
}
