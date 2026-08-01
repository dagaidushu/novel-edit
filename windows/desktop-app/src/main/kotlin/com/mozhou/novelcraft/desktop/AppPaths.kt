package com.mozhou.novelcraft.desktop

import java.nio.file.Files
import java.nio.file.Path

data class AppPaths(val root: Path, val database: Path, val covers: Path, val recovery: Path, val portable: Boolean) {
    companion object {
        fun resolve(): AppPaths {
            val portable = System.getenv("NOVELEDIT_PORTABLE") == "1" || System.getenv("NOVELCRAFT_PORTABLE") == "1"
            val root = if (portable) {
                Path.of(System.getProperty("user.dir"), "data")
            } else {
                migrateLegacyRoot(Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "NovelEdit"))
            }
            val newDatabase = root.resolve("noveledit.db")
            val legacyDatabase = root.resolve("novelcraft.db")
            val database = if (Files.exists(newDatabase) || !Files.exists(legacyDatabase)) newDatabase else legacyDatabase
            val result = AppPaths(root, database, root.resolve("covers"), root.resolve("recovery"), portable)
            Files.createDirectories(result.covers)
            Files.createDirectories(result.recovery)
            return result
        }

        private fun migrateLegacyRoot(target: Path): Path {
            if (Files.exists(target)) return target
            val legacy = target.resolveSibling("NovelCraft")
            if (!Files.exists(legacy)) return target
            return runCatching {
                Files.move(legacy, target)
                val oldDatabase = target.resolve("novelcraft.db")
                if (Files.exists(oldDatabase)) Files.move(oldDatabase, target.resolve("noveledit.db"))
                listOf("-wal", "-shm").forEach { suffix ->
                    val oldSidecar = target.resolve("novelcraft.db$suffix")
                    if (Files.exists(oldSidecar)) Files.move(oldSidecar, target.resolve("noveledit.db$suffix"))
                }
                target
            }.getOrElse { legacy }
        }
    }
}
