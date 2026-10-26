// ルートプロジェクトのビルドスクリプト。stonecutter が settings.gradle.kts の `create(rootProject)` で
// このファイルをルートのビルドスクリプトに割り当てるため、`build.gradle.kts` は読まれない
// （各構成のビルドスクリプトは settings.gradle.kts の `buildscript(...)` で指定した build.*.gradle.kts）。
// 全構成に共通の設定は、ここの subprojects ブロックに書く。
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-fabric"

val modId = "mapscribble"

subprojects {
    // 各構成のビルドスクリプトが java プラグインを適用した時点で、全構成に共通の設定を足す
    pluginManager.withPlugin("java") {
        // jar と sources jar に LICENSE を同梱する（`LICENSE_mapscribble`）。
        // Fabric 難読化版の remapJar や旧 Forge の reobfJar は入力の jar の中身を引き継ぐので、
        // そこへ足すと同名のファイルが二重に入る。タスク名で絞る。
        // rename のラムダがスクリプト自体を抱えないよう、文字列とファイルをローカルに取り出して使う
        val licenseSuffix = "_$modId"
        val licenseFile = rootProject.file("LICENSE")
        tasks.withType<Jar>().matching { it.name == "jar" || it.name == "sourcesJar" }.configureEach {
            inputs.property("mod_id", licenseSuffix)
            from(licenseFile) { rename { "$it$licenseSuffix" } }
        }

        // 全構成の最終 jar を `build/libs/<MOD版>/` へ集める（CI のビルド・リリースと README が呼ぶ）。
        // jar 名は `mapscribble-<MOD版>+<Minecraft版>-<ローダー>.jar`（各構成が version に付ける）なので、
        // 同じ Minecraft 版の Fabric 版と NeoForge 版（1.20.1 は Fabric 版と Forge 版）が
        // 同名で上書きし合うことは無い。
        // 最終 jar は `assemble` が `build/libs/` に出す（Fabric 難読化版は remapJar、旧 Forge は reobfJar、
        // Fabric 非難読化版と NeoForge は jar の出力）。ファイル名で絞るので sources jar は集めない
        tasks.register<Copy>("buildAndCollect") {
            group = "build"
            description = "Builds the mod jar and copies it to `build/libs/{mod version}/` in the root project"

            val archivesName = project.extensions.getByType<BasePluginExtension>().archivesName
            val jarName = archivesName.map { "$it-${project.version}.jar" }.get()
            val collectDir = rootProject.layout.buildDirectory.dir("libs/${rootProject.version}")

            dependsOn("assemble", tasks.matching { it.name == "reobfJar" || it.name == "remapJar" })
            inputs.property("jar_name", jarName)
            from(project.layout.buildDirectory.dir("libs")) { include(jarName) }
            into(collectDir)

            doLast {
                check(collectDir.get().file(jarName).asFile.isFile) {
                    "$jarName が ${collectDir.get().asFile} に集まっていません（assemble が build/libs に最終 jar を出していない可能性があります）"
                }
            }
        }
    }
}
