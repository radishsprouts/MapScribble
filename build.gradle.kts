// 全サブプロジェクトの共通設定。個別の依存は各 build.*.gradle.kts に書く。

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

    val propFile = file("gradle.properties")
    if (propFile.exists()) {
        val p = java.util.Properties()
        propFile.inputStream().use { p.load(it) }
        for (name in p.stringPropertyNames()) {
            project.extensions.extraProperties.set(name, p.getProperty(name))
        }
    }
}

val modId = "mapscribble"

subprojects {
    // 各構成のビルドスクリプトが java プラグインを適用した時点で、全構成に共通の設定を足す
    pluginManager.withPlugin("java") {
        // jar と sources jar に LICENSE を同梱する（`LICENSE_mapscribble`）。
        // Fabric の remapJar 等は入力の jar の中身を引き継ぐので、足すと同名のファイルが二重に入る。名前で絞る
        // rename のラムダがスクリプト自体を抱えないよう、文字列をローカルに取り出して使う
        val licenseSuffix = "_$modId"
        tasks.withType<Jar>().matching { it.name == "jar" || it.name == "sourcesJar" }.configureEach {
            inputs.property("mod_id", licenseSuffix)
            from(rootProject.file("LICENSE")) { rename { "$it$licenseSuffix" } }
        }

        // 全構成の最終 jar を `build/libs/<MOD版>/` へ集める（CI のビルド・リリースと README が呼ぶ）。
        // jar 名は `mapscribble-<MOD版>+<Minecraft版>-<ローダー>.jar` なので、同じ Minecraft 版の Fabric 版と
        // NeoForge 版（1.20.1 は Fabric 版と Forge 版）が同名で上書きし合うことは無い。
        // 最終 jar は `assemble` が `build/libs/` に出す（Fabric 難読化版は remapJar、旧 Forge は reobfJar の出力。
        // `jar` 単体は `build/devlibs/` に出る開発用）。ファイル名で絞るので sources jar は集めない
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
