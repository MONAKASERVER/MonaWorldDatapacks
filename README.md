# MonaWorldDatapacks

Paper 1.21.11 + Multiverse-Core 5.x向けの、**worldgenデータパックを対象Dimension専用のリソースへ変換する**プラグインです。

Minecraftのデータパックはサーバー全体のdata registryへ読み込まれます。本プラグインは元パックをそのまま有効化せず、既知のworldgenリソースだけをワールド専用namespaceへcloneし、参照をJSON tree上で書き換え、専用Dimensionを生成します。

> [!IMPORTANT]
> 「Minecraftの全データパック機能を完全にper-world化する」プラグインではありません。recipe、advancement、enchantment、damage type、load/tick functionなどはserver-globalです。strict modeでは安全に分離できないパックを登録しません。

## 対象環境

- Minecraft Java Edition 1.21.11（Data Pack version 94.1）
- Paper 1.21.11
- Java 21
- Multiverse-Core 5.8.0（5.x正式API）
- Gradle 8.10.2

Paperのexperimentalな[Datapack Discovery API](https://docs.papermc.io/paper/dev/lifecycle/datapacks/)を使用します。Paper依存部分は`DatapackDiscoveryAdapter`へ隔離しています。

## 仕組み

1. `PluginBootstrap`が設定と割り当てを起動初期に読む。
2. `packs/*.zip`をZip Slip/展開量制限付きで解析する。
3. リソース、既知のResourceLocation参照、依存、循環、欠損、競合を解析する。
4. strict safety policyを通過した割り当てだけを対象namespaceへ変換する。
5. `compiled/<world>.zip.tmp`へ書き、atomic moveで確定する。
6. `LifecycleEvents.DATAPACK_DISCOVERY`で生成ZIPだけをdiscover/auto-enableする。
7. registry構築後、Multiverse-Coreの`MultiverseCoreApi.whenLoaded(...)`と`ImportWorldOptions.worldKey(...)`で名前付きDimensionを認識させる。

起動前のDiscovery deadlineより後へ処理を逃がすとregistryへ間に合わないため、初回コンパイルはBootstrap lifecycle内で完了させます。通常稼働中の`scan`/`compile`コマンドは非同期で実行し、起動時はSHA-256キャッシュで再変換を避けます。

元ZIP、Multiverse設定、world folder、`level.dat`は変更・削除しません。通常の`WorldCreator`だけでデータパックDimensionを模倣する処理もありません。

## インストール

1. Paper 1.21.11とMultiverse-Core 5.xを用意する。
2. `MonaWorldDatapacks-1.0.0.jar`を`plugins/`へ置く。
3. 一度起動して`plugins/MonaWorldDatapacks/`を生成する。
4. 停止後、元データパックZIPを`packs/`へ置く。
5. `worlds.yml`へ割り当てを記述する。
6. サーバーを通常再起動する。`/reload`は使用しない。

```text
plugins/MonaWorldDatapacks/
├─ config.yml
├─ worlds.yml
├─ packs/
├─ compiled/
├─ cache/
├─ reports/
└─ backups/
```

## 割り当て例

### Incendium用Nether

```yaml
worlds:
  resource_nether:
    enabled: true
    environment: NETHER
    datapacks:
      - id: incendium
        priority: 100
    profile: INCENDIUM
    source-dimension: minecraft:the_nether
    strategy: SCOPED_WORLDGEN
    existing-world-policy: REFUSE
```

### Stellarity用End

```yaml
worlds:
  resource_end:
    enabled: true
    environment: THE_END
    datapacks:
      - id: stellarity
        priority: 100
    profile: STELLARITY
    source-dimension: minecraft:the_end
    strategy: SCOPED_WORLDGEN
    existing-world-policy: REFUSE
```

複数パックはpriorityが高いものを優先します。競合はreportへ記録され、同点はpack idで決定されます。

## safety設定

```yaml
safety:
  strict-mode: true
  reject-global-registry-overrides: true
  reject-unknown-resources: true
  reject-existing-generated-world: true
  allow-experimental-runtime-scope: false
```

- `REFUSE`: 既に`level.dat`または`region/`があるworldへの適用を拒否（既定）。
- `ALLOW_NEW_CHUNKS_ONLY`: 既存chunkとの境界リスクを管理者が受け入れるモード。ただしglobal safety設定が優先されます。
- `FORCE`: 強制許可。事前バックアップ必須です。

プラグインがworldを削除する機能はありません。

## Compatibility分類

- `WORLDGEN_SCOPABLE`: 専用namespaceへclone可能なDimension/worldgen registry。
- `RUNTIME_SCOPABLE`: 条件付きで扱えるが意味上の完全分離を保証できない要素。
- `SERVER_GLOBAL`: recipe、advancement、function等。自動では含めない。
- `UNKNOWN`: schemaを推測せず、strict modeでは拒否。
- `UNSUPPORTED`: 壊れたJSON、欠損したpack-owned参照、不正構造など。

JSON内の文字列を一括置換しません。既知のregistry schema fieldとtag valuesだけをResourceLocationとして扱い、説明文などは変更しません。`minecraft:*`は組み込み参照として維持し、元パックが実際にoverrideしたworldgen entryだけをcloneします。

## コマンド

`/mwd`（alias: `/monaworlddatapacks`, `/mvdp`）

| コマンド | 用途 |
|---|---|
| `/mwd status` | 起動時コンパイル状態 |
| `/mwd packs` | ZIP一覧 |
| `/mwd worlds` | 割り当て一覧 |
| `/mwd info <world>` | ワールド詳細 |
| `/mwd scan <pack>` | 解析結果 |
| `/mwd report <pack>` | Markdown report生成 |
| `/mwd assign <world> <pack> [profile]` | worlds.ymlへ追加 |
| `/mwd unassign <world> <pack>` | worlds.ymlから解除 |
| `/mwd compile [world]` | 次回起動用に再コンパイル |
| `/mwd doctor [world]` | Paper/Java/MV/compiled/world状態診断 |
| `/mwd reload-config` | 構文だけ再検証（registryはreloadしない） |
| `/mwd version` | バージョン |

`assign`、`unassign`、`compile`後の反映には再起動が必要です。

## permissions

- `monaworlddatapacks.admin`
- `monaworlddatapacks.status`
- `monaworlddatapacks.scan`
- `monaworlddatapacks.compile`
- `monaworlddatapacks.assign`
- `monaworlddatapacks.doctor`

既定はOPです。

## キャッシュとZIP保護

キャッシュキーには、元ZIP SHA-256、world assignment、priority、transform version、Minecraft version、profileを含めます。ZIPでは絶対path、`..`、重複entryを拒否し、archive size、展開総量、単一ファイル、ファイル数を制限します。検証途中で例外になったZIPも必ずcloseします。

## 対応状況と既知の制限

| 項目 | 状態 | 補足 |
|---|---|---|
| 既知worldgen JSONのnamespace分離 | SUPPORTED | synthetic integration test済み |
| Vanilla Nether + Incendium風Nether | SUPPORTED（コンパイラ） | 実Incendiumの版ごとに`/mwd scan`と実サーバーテストが必要 |
| Vanilla End + Stellarity風End | SUPPORTED（コンパイラ） | 同上 |
| real Incendium/Stellarityのruntime機能 | PARTIAL | function/loot等は除外。strictではglobal要素があるパックを拒否 |
| recipe/advancement/enchantment/damage type | UNSUPPORTED per-world | Minecraftのserver-global registry |
| load/tick function | UNSUPPORTED in strict | selectorや副作用をDimensionへ完全拘束できない |
| 未知registry/schema | UNSUPPORTED in strict | 推測変換しない |
| 稼働中のworldgen reload | UNSUPPORTED | registry freezeのため再起動必須 |
| 既存生成world | REFUSED by default | chunk境界・registry mismatch防止 |

データパックの内部構造はリリースごとに変わります。「Incendium」「Stellarity」という名前だけで安全性を仮定せず、実ファイル解析結果を優先します。realパック本体はリポジトリへ同梱しません。

### 重要なMinecraft/Paper上の制約

custom dimensionのregistry entryは生成できますが、Minecraft/PaperがそのDimensionをロードし、Multiverseがnamespaced world keyをimportできる必要があります。実パックと実サーバーの組み合わせを確認するまでは、公開ワールドへ直接適用しないでください。import失敗時は生成パックをglobalな元パックへ差し替えず、詳細ログを残します。

## 手動integration test

1. テスト用サーバーをバックアップする。
2. `world`をVanillaのままにする。
3. `test_nether`へIncendium風パックを割り当て、`vanilla_nether`は割り当てない。
4. 起動ログで`Discovered generated datapack`とMultiverse mappingを確認する。
5. `/datapack list`、`/mwd doctor test_nether`、`/mv tp test_nether`を確認する。
6. 両Netherで新chunkを生成し、custom biomeが`test_nether`だけに存在することを確認する。
7. 再起動後もworld keyと生成が維持されることを確認する。
8. 元ZIPがglobal enableされていないことを再確認する。

## architecture

- `bootstrap`: 起動前コンパイルとLifecycle登録
- `pack`, `security`: ZIP検証、metadata/resource解析
- `resource`: resource key/reference/dependency graph
- `compatibility`: scope分類と拒否理由
- `compiler`: conflict解決、namespace mapping、JSON tree変換、Dimension生成
- `profile`: Generic/Incendium/Stellarityの差分検証
- `paper`: experimental Discovery API adapter
- `multiverse`: Multiverse-Core 5正式API adapter
- `diagnostic`, `command`: doctorとPaper Command API

NMSやreflectionは使用していません。

## build

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

成果物: `build/libs/MonaWorldDatapacks-1.0.1.jar`

`resource_nether`と`resource_end`は設定例であり、固定されたワールド名ではありません。たとえば次のように任意名を割り当てられます。

```text
/mwd assign test_nether incendium INCENDIUM
/mwd compile test_nether
```

`assign`と`unassign`は保存後すぐコマンド用設定へ再読込されます。生成済みデータパックのDiscoveryとワールドへの実適用には、引き続き通常再起動が必要です。

JUnit 5にはResourceLocation、namespace mapper、JSON rewrite、dependency graph、cycle/missing reference、SHA-256、Zip Slip/size limit、world名sanitizer、priority/conflict、Incendium/Stellarity風fixture、生成ZIPのglobal汚染防止テストが含まれます。
