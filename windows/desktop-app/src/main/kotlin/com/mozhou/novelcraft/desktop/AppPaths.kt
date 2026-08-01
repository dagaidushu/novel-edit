package com.mozhou.novelcraft.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class AppPaths(val root: Path, val database: Path, val covers: Path, val recovery: Path, val portable: Boolean) {
    companion object {
        fun resolve(): AppPaths {
            val portable = System.getenv("NOVELEDIT_PORTABLE") == "1" || System.getenv("NOVELCRAFT_PORTABLE") == "1"
            val standardRoot = standardRoot()
            val root = if (portable) {
                Path.of(System.getProperty("user.dir"), "data")
            } else {
                val configuredRoot = configuredRoot()
                if (configuredRoot != null) {
                    completePendingMigration(configuredRoot)
                    configuredRoot
                } else {
                    migrateLegacyRoot(standardRoot)
                }
            }
            val newDatabase = root.resolve("noveledit.db")
            val legacyDatabase = root.resolve("novelcraft.db")
            val database = if (Files.exists(newDatabase) || !Files.exists(legacyDatabase)) newDatabase else legacyDatabase
            val result = AppPaths(root, database, root.resolve("covers"), root.resolve("recovery"), portable)
            Files.createDirectories(result.covers)
            Files.createDirectories(result.recovery)
            return result
        }

        fun scheduleDataDirectoryMigration(source: Path, destination: Path) {
            val sourceRoot = source.toAbsolutePath().normalize()
            val targetRoot = destination.toAbsolutePath().normalize()
            require(sourceRoot != targetRoot) { "新数据目录与当前目录相同" }
            require(!targetRoot.startsWith(sourceRoot)) { "新数据目录不能位于当前数据目录内" }
            require(!sourceRoot.startsWith(targetRoot)) { "新数据目录不能是当前数据目录的上级目录" }
            if (Files.exists(targetRoot)) {
                Files.list(targetRoot).use { entries ->
                    require(!entries.findAny().isPresent) { "请选择一个空文件夹，避免覆盖其中已有文件" }
                }
            }
            Files.createDirectories(settingsRoot())
            Files.writeString(locationFile(), targetRoot.toString())
            Files.writeString(migrationSourceFile(), sourceRoot.toString())
        }

        private fun standardRoot(): Path = Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "NovelEdit")
        private fun settingsRoot(): Path = Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "NovelEdit-Settings")
        private fun locationFile(): Path = settingsRoot().resolve("data-root.txt")
        private fun migrationSourceFile(): Path = settingsRoot().resolve("data-migration-source.txt")

        private fun configuredRoot(): Path? = runCatching {
            locationFile().takeIf(Files::exists)?.let { file ->
                Files.readString(file).trim().takeIf(String::isNotEmpty)?.let { Path.of(it).toAbsolutePath().normalize() }
            }
        }.getOrNull()

        private fun completePendingMigration(destination: Path) {
            val sourceFile = migrationSourceFile()
            if (!Files.exists(sourceFile)) return
            val source = runCatching { Path.of(Files.readString(sourceFile).trim()).toAbsolutePath().normalize() }.getOrNull()
                ?: return
            if (source != destination && Files.exists(source)) {
                Files.createDirectories(destination)
                Files.list(source).use { entries ->
                    entries.forEach { child ->
                        val target = destination.resolve(child.fileName.toString())
                        require(!Files.exists(target)) { "无法迁移数据：目标目录已有 ${child.fileName}" }
                        runCatching { Files.move(child, target, StandardCopyOption.ATOMIC_MOVE) }
                            .getOrElse { Files.move(child, target) }
                    }
                }
                runCatching { Files.deleteIfExists(source) }
            }
            Files.deleteIfExists(sourceFile)
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
