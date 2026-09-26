package com.ridevibe.core.network.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the Retrofit services to the OpenAPI specs in docs/api so the two
 * cannot drift silently. The backend owns the specs (see CLAUDE.md section 5):
 * if this test fails, either the Kotlin call or the spec was changed alone.
 *
 * Deliberately text-based (no YAML or Kotlin parsing libraries): it reads the
 * path keys under `paths:` and the `@GET("...")`-style annotations, which is
 * enough to compare the two surfaces and needs no extra dependencies.
 */
class ApiContractTest {

    private val docsApiDir: File by lazy { locateDocsApiDir() }
    private val apiSourceDir = File("src/main/java/com/ridevibe/core/network/api")

    @Test
    fun `passenger service matches openapi yaml`() {
        val specPaths = specPaths(File(docsApiDir, "openapi.yaml"))
        val servicePaths = retrofitPaths(File(apiSourceDir, "CrsApiService.kt"))
        assertSameSet("CrsApiService", "docs/api/openapi.yaml", servicePaths, specPaths)
    }

    @Test
    fun `staff service matches staff openapi yaml`() {
        val specPaths = specPaths(File(docsApiDir, "staff-openapi.yaml"))
        val servicePaths = retrofitPaths(File(apiSourceDir, "StaffApiService.kt"))
        assertSameSet("StaffApiService", "docs/api/staff-openapi.yaml", servicePaths, specPaths)
    }

    // A staff call must never reach a passenger route (it would carry a staff
    // token onto /v1), and a passenger call must never reach a staff route
    // (it would rely on X-Device-Id where a token is required).
    @Test
    fun `staff service never calls passenger routes`() {
        val offenders = retrofitPaths(File(apiSourceDir, "StaffApiService.kt"))
            .filter { it.startsWith("/v1/") }
        assertTrue("StaffApiService must not call /v1 routes: $offenders", offenders.isEmpty())
    }

    @Test
    fun `passenger service never calls staff routes`() {
        val staffPrefixes = listOf("/admin/", "/partner/", "/auth/")
        val offenders = retrofitPaths(File(apiSourceDir, "CrsApiService.kt"))
            .filter { path -> staffPrefixes.any { path.startsWith(it) } }
        assertTrue("CrsApiService must not call staff routes: $offenders", offenders.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun assertSameSet(
        serviceName: String,
        specName: String,
        servicePaths: Set<String>,
        specPaths: Set<String>,
    ) {
        assertTrue("No Retrofit paths found in $serviceName", servicePaths.isNotEmpty())
        assertTrue("No paths found in $specName", specPaths.isNotEmpty())
        val onlyInService = (servicePaths - specPaths).sorted()
        val onlyInSpec = (specPaths - servicePaths).sorted()
        assertEquals(
            "$serviceName and $specName disagree.\n" +
                "  In $serviceName but not in $specName: $onlyInService\n" +
                "  In $specName but not in $serviceName: $onlyInSpec",
            emptyList<String>() to emptyList<String>(),
            onlyInService to onlyInSpec,
        )
    }

    /** Path keys under the top-level `paths:` block, e.g. `/v1/trips/{tripId}`. */
    private fun specPaths(spec: File): Set<String> {
        assertTrue("Spec not found: ${spec.absolutePath}", spec.isFile)
        val pathKey = Regex("""^  (/\S+):\s*$""")
        var inPaths = false
        return spec.readLines()
            .mapNotNull { rawLine ->
                val line = rawLine.trimEnd()
                when {
                    line == "paths:" -> { inPaths = true; null }
                    // Any other top-level key ends the paths block.
                    inPaths && line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#") -> {
                        inPaths = false; null
                    }
                    inPaths -> pathKey.find(line)?.groupValues?.get(1)?.let(::normalise)
                    else -> null
                }
            }
            .toSet()
    }

    /** Relative paths from `@GET("...")`, `@POST("...")`, etc., made absolute. */
    private fun retrofitPaths(service: File): Set<String> {
        assertTrue("Service not found: ${service.absolutePath}", service.isFile)
        val annotation = Regex("""@(?:GET|POST|PATCH|PUT|DELETE)\(\s*"([^"]+)"\s*\)""")
        return annotation.findAll(service.readText())
            .map { normalise(it.groupValues[1]) }
            .toSet()
    }

    /** Leading slash, no trailing slash, no query string; `{param}` kept as is. */
    private fun normalise(path: String): String =
        "/" + path.substringBefore('?').trim('/')

    /**
     * Gradle runs unit tests with the module directory as the working directory,
     * so the specs are at ../docs/api. Walk upward as a fallback for IDE runners
     * that start elsewhere.
     */
    private fun locateDocsApiDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "docs/api")
            if (File(candidate, "openapi.yaml").isFile) return candidate
            dir = dir.parentFile
        }
        error("docs/api not found above ${File("").absolutePath}")
    }
}
