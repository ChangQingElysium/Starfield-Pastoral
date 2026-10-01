# 1.21.1 GameTest 基准（快照 d453632）

5 次全量运行（`-PgameTestNamespaces=` 全部命名空间，808 项，含仅用于验证的 `dumpcontent`）。日志在 `build/port-parity/baseline-d453632-gametest*.log`（本地）。

- 稳定通过：793 项。1.20.1 移植版必须同样稳定通过。
- 5/5 失败（1.21.1 在研改动本身未通过，非移植问题）：
  wizardbuildingsplaceonsolidgroundineverydirection, roostanchorbreakwakesbatandfreezestopsflight,
  nativebatroostwakereloadanddeath, nativebatnoticespursuesanddealscontactdamage,
  missinglegacyroomandfailedarrivalremainsafe, migrationdoortraveltransferandmissingfloor,
  impossiblecellcleansupwithoutloot, fourbouncesthenfifthcollisiondestroys,
  flyemergesgraduallyandstopsbelowlowceiling, flowingwatercannotwashawayrepresentativeblocks,
  fireballhasthreebouncesandstaysatitslaunchheight, armoredbirthbesidewall,
  allvariantsflywithoutplayersandkeepidentityafterreload
- 不稳定（1.21.1 自身）：sourcesightthrowandreload（4/5 失败）、mainextensionandbombuseactualbreakerinsteadofnearbyplayer（1/5 失败）。移植版按相同次数运行，失败率不得明显高于基准。
