"""Names for reviewed Ginger Island objects, in every shipped language.

Original furniture/machine names reuse original locale strings. Scene-component
names below are explicit translations, never title-cased filenames or English
placeholders. Generated game resources do not depend on the local source tree.
"""
import json
from pathlib import Path
from ginger_island_catalog import SOURCE_NAMES

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = ['en_us', 'zh_cn', 'de_de', 'es_es', 'fr_fr', 'it_it', 'hu_hu', 'ja_jp', 'ko_kr', 'pt_br', 'ru_ru', 'tr_tr']
LOCALES = ['', 'zh-CN', 'de-DE', 'es-ES', 'fr-FR', 'it-IT', 'hu-HU', 'ja-JP', 'ko-KR', 'pt-BR', 'ru-RU', 'tr-TR']

ENTITY_NAMES = {
    'ginger_giant_turtle': ['Giant Island Turtle', '姜岛大海龟', 'Riesige Inselschildkröte',
        'Tortuga gigante de la isla', 'Tortue géante de l’île', 'Tartaruga gigante dell’isola',
        'Óriás szigeti teknős', '島の大きなウミガメ', '섬의 거대 바다거북',
        'Tartaruga gigante da ilha', 'Гигантская островная черепаха', 'Dev ada kaplumbağası'],
}

ROWS = '''
music_crystal|Music Crystal|音乐水晶|Musikkristall|Cristal musical|Cristal musical|Cristallo musicale|Zenei kristály|音楽の水晶|음악 수정|Cristal musical|Музыкальный кристалл|Müzik kristali
island_arrival_totem|Island Arrival Totem|姜岛传送落点柱|Insel-Ankunftstotem|Tótem de llegada a la isla|Totem d'arrivée sur l'île|Totem di arrivo sull'isola|Szigeti érkezési totem|島への到着トーテム|섬 도착 토템|Totem de chegada à ilha|Тотем прибытия на остров|Adaya varış totemi
willy_boat|Willy's Boat|威利的船|Willys Boot|Barco de Willy|Bateau de Willy|Barca di Willy|Willy hajója|ウィリーの船|윌리의 배|Barco do Willy|Лодка Вилли|Willy'nin teknesi
parrot_perch|Parrot Perch|鹦鹉栖木|Papageiensitz|Posadero de loro|Perchoir à perroquet|Posatoio del pappagallo|Papagájülőke|オウムの止まり木|앵무새 횃대|Poleiro de papagaio|Жёрдочка попугая|Papağan tüneği
south_palm|Mature Island Palm|姜岛成熟棕榈|Ausgewachsene Inselpalme|Palmera adulta de la isla|Palmier adulte de l’île|Palma matura dell’isola|Kifejlett szigeti pálma|島の成木のヤシ|섬의 다 자란 야자수|Palmeira adulta da ilha|Взрослая островная пальма|Olgun ada palmiyesi
banana_altar_assembly|Banana Altar|香蕉供台|Bananenaltar|Altar de plátanos|Autel à bananes|Altare delle banane|Banánoltár|バナナの祭壇|바나나 제단|Altar de bananas|Банановый алтарь|Muz sunağı
island_torch_unlit|Altar Torch|供台火炬|Altarfackel|Antorcha del altar|Torche de l'autel|Torcia dell'altare|Oltárfáklya|祭壇のたいまつ|제단 횃불|Tocha do altar|Факел алтаря|Sunak meşalesi
gem_pedestal|Gem Pedestal|宝石供台|Edelsteinsockel|Pedestal de gemas|Piédestal à gemmes|Piedistallo delle gemme|Drágakőtalapzat|宝石の台座|보석 받침대|Pedestal de gemas|Пьедестал самоцветов|Değerli taş kaidesi
gem_shrine_assembly|Gem Bird Shrine|宝石鸟神龛|Edelsteinvogelschrein|Santuario de aves de gemas|Sanctuaire des oiseaux-gemmes|Santuario degli uccelli delle gemme|Drágakőmadár-szentély|宝石鳥の祠|보석 새 사당|Santuário dos pássaros de gemas|Святилище самоцветных птиц|Değerli taş kuşları tapınağı
marker_north|Shrine Direction Marker|神龛方位石|Richtungsstein des Schreins|Marca de dirección del santuario|Repère du sanctuaire|Indicatore del santuario|Szentélyirány-jelző|祠の方角標石|사당 방향 표식|Marco de direção do santuário|Указатель святилища|Tapınak yön işareti
hut_container_small|Small Woven Basket|小编织篓|Kleiner Flechtkorb|Cesta tejida pequeña|Petit panier tressé|Cesto intrecciato piccolo|Kis fonott kosár|小さな編みかご|작은 바구니|Cesto trançado pequeno|Маленькая плетёная корзина|Küçük örgü sepet
hut_container_tall|Tall Woven Basket|高编织篓|Hoher Flechtkorb|Cesta tejida alta|Grand panier tressé|Cesto intrecciato alto|Magas fonott kosár|背の高い編みかご|키 큰 바구니|Cesto trançado alto|Высокая плетёная корзина|Uzun örgü sepet
hut_grass_bedding|Grass Bedding|草铺|Graslager|Lecho de hierba|Couche d'herbe|Giaciglio d'erba|Fűfekhely|草の寝床|풀 침상|Cama de capim|Травяная подстилка|Ot yatak
golden_walnut_bush_rework|Golden Walnut Bush|金核桃灌木|Goldnussstrauch|Arbusto de nueces doradas|Buisson de noix dorées|Cespuglio di noci dorate|Aranydióbokor|金のクルミの茂み|황금 호두 덤불|Arbusto de nozes douradas|Куст золотых орехов|Altın ceviz çalısı
slingshot_walnut_target|Slingshot Walnut Target|弹弓核桃靶|Goldnuss-Schleuderziel|Blanco de nuez para tirachinas|Cible de noix pour lance-pierre|Bersaglio di noce per fionda|Csúzlival eltalálható dió|パチンコ用クルミの的|새총 호두 표적|Alvo de noz para estilingue|Ореховая мишень для рогатки|Sapanla vurulan ceviz hedefi
professor_boulder|Professor's Cave Boulder|教授洞口巨石|Fels am Professorenhöhleneingang|Roca de la cueva del profesor|Rocher de la grotte du professeur|Masso della grotta del professore|Professzor barlangjának sziklája|教授の洞窟の大岩|교수 동굴 입구 바위|Rocha da caverna do professor|Валун у пещеры профессора|Profesörün mağara kayası
dig_ammonite_relief|Ammonite Relief|菊石浮雕|Ammonitenrelief|Relieve de amonita|Relief d'ammonite|Rilievo di ammonite|Ammoniteszdombormű|アンモナイトの浮彫|암모나이트 부조|Relevo de amonite|Рельеф аммонита|Ammonit kabartması
dig_fossil_fragments|Fossil Fragments|化石碎片堆|Fossilfragmente|Fragmentos de fósiles|Fragments de fossiles|Frammenti fossili|Fossziliatöredékek|化石のかけら|화석 조각 더미|Fragmentos de fósseis|Обломки окаменелостей|Fosil parçaları
field_office_fossil_backing|Fossil Display Board|化石陈列底板|Fossil-Schautafel|Panel de exposición de fósiles|Panneau d'exposition de fossiles|Pannello espositivo di fossili|Fossziliakiállító tábla|化石の展示板|화석 전시판|Painel de exposição de fósseis|Щит для окаменелостей|Fosil sergileme panosu
field_office_fossil_display|Fossil Display Stand|化石展台|Fossil-Ausstellungstisch|Mesa de exposición de fósiles|Présentoir à fossiles|Espositore di fossili|Fossziliakiállító asztal|化石の展示台|화석 전시대|Mesa de exposição de fósseis|Стол для окаменелостей|Fosil sergileme masası
field_office_radio|Field Office Radio|考察站收音机|Radio der Feldstation|Radio de la oficina de campo|Radio du bureau de terrain|Radio dell'ufficio sul campo|Kutatóállomási rádió|調査所のラジオ|현장 사무소 라디오|Rádio do escritório de campo|Радио полевой станции|Saha ofisi radyosu
field_office_survey_board|Field Survey Board|考察调查板|Forschungstafel|Panel de investigación|Panneau d'enquête|Pannello delle indagini|Kutatási tábla|調査ボード|조사 게시판|Quadro de pesquisa|Доска исследований|Araştırma panosu
field_office_canvas_floor|Tent Canvas Floor|帐篷帆布地板|Zeltboden aus Segeltuch|Suelo de lona de la tienda|Sol de tente en toile|Pavimento in tela della tenda|Sátorponyva-padló|テントの帆布床|텐트 캔버스 바닥|Piso de lona da barraca|Брезентовый пол палатки|Çadır bez zemini
professor_work_desk|Professor's Work Desk|教授工作柜台|Arbeitstisch des Professors|Mesa de trabajo del profesor|Bureau du professeur|Banco di lavoro del professore|Professzor munkaasztala|教授の作業机|교수 작업대|Mesa de trabalho do professor|Рабочий стол профессора|Profesörün çalışma masası
professor_field_tent_assembly|Professor's Field Tent|教授考察帐篷|Forschungszelt des Professors|Tienda de campo del profesor|Tente de terrain du professeur|Tenda del professore|Professzor kutatósátra|教授の調査テント|교수 현장 텐트|Barraca de campo do professor|Полевая палатка профессора|Profesörün saha çadırı
golden_parrot_perch|Golden Parrot Perch|金色鹦鹉栖木|Goldener Papageiensitz|Posadero de loro dorado|Perchoir à perroquet doré|Posatoio dorato del pappagallo|Arany papagájülőke|金色のオウムの止まり木|황금 앵무새 횃대|Poleiro dourado de papagaio|Золотая жёрдочка попугая|Altın papağan tüneği
north_cliff_block|Island Cliff|姜岛崖壁|Inselklippe|Acantilado de la isla|Falaise de l'île|Scogliera dell'isola|Szigeti sziklafal|島の崖|섬 절벽|Penhasco da ilha|Островная скала|Ada kayalığı
north_cliff_inner_corner|Island Cliff Inner Corner|姜岛崖壁内角|Innenecke der Inselklippe|Esquina interior del acantilado|Angle intérieur de falaise|Angolo interno della scogliera|Sziklafal belső sarok|島の崖の内角|섬 절벽 안쪽 모서리|Canto interno do penhasco|Внутренний угол скалы|Ada kayalığı iç köşesi
north_cliff_outer_corner|Island Cliff Outer Corner|姜岛崖壁外角|Außenecke der Inselklippe|Esquina exterior del acantilado|Angle extérieur de falaise|Angolo esterno della scogliera|Sziklafal külső sarok|島の崖の外角|섬 절벽 바깥 모서리|Canto externo do penhasco|Внешний угол скалы|Ada kayalığı dış köşesi
north_cliff_slab|Island Cliff Slab|姜岛崖壁台阶|Inselklippenstufe|Losa de acantilado|Dalle de falaise|Lastra della scogliera|Sziklafallap|島の崖のハーフブロック|섬 절벽 반 블록|Laje de penhasco|Плита островной скалы|Ada kayalığı basamağı
north_cliff_stairs|Island Cliff Stairs|姜岛崖壁楼梯|Inselklippentreppe|Escaleras de acantilado|Escalier de falaise|Scale della scogliera|Sziklafallépcső|島の崖の階段|섬 절벽 계단|Escadas de penhasco|Ступени островной скалы|Ada kayalığı merdiveni
island_red_cooker|Red Island Stove|姜岛红色炉灶|Roter Inselherd|Cocina roja de la isla|Cuisinière rouge de l'île|Cucina rossa dell'isola|Piros szigeti tűzhely|島の赤いコンロ|섬의 빨간 조리대|Fogão vermelho da ilha|Красная островная плита|Adanın kırmızı ocağı
stove_fireplace|Island Fireplace|姜岛壁炉|Inselkamin|Chimenea de la isla|Cheminée de l'île|Camino dell'isola|Szigeti kandalló|島の暖炉|섬 벽난로|Lareira da ilha|Островной камин|Ada şöminesi
farm_return_obelisk|Farm Return Obelisk|农场回程图腾柱|Farm-Rückkehr-Obelisk|Obelisco de regreso a la granja|Obélisque de retour à la ferme|Obelisco di ritorno alla fattoria|Farmra visszavezető obeliszk|牧場への帰還オベリスク|농장 귀환 오벨리스크|Obelisco de retorno à fazenda|Обелиск возвращения на ферму|Çiftliğe dönüş dikilitaşı
island_beach_broken_timber|Beach Broken Timber|海滩断木|Strand-Bruchholz|Madera rota de la playa|Bois brisé de plage|Legname spezzato sulla spiaggia|Parti törött fa|砂浜の折れた木材|해변 부러진 목재|Madeira quebrada da praia|Сломанный брус на пляже|Sahildeki kırık kereste
island_beach_driftwood_pile|Beach Driftwood Pile|海滩漂流木堆|Strand-Treibholzhaufen|Montón de madera a la deriva|Tas de bois flotté|Mucchio di legni alla deriva|Parti uszadékfakupac|砂浜の流木の山|해변 유목 더미|Pilha de madeira flutuante|Куча плавника на пляже|Sahil odun yığını
farm_ruin_boarding|Farmhouse Ruin Boarding|农舍废墟封板|Bretter der Farmhausruine|Tablones de la casa en ruinas|Planches de la ferme en ruine|Assi della fattoria in rovina|Romos farmház deszkázata|廃農家の板張り|폐가 판자|Tábuas da casa em ruínas|Доски разрушенного дома|Harap çiftlik evi tahtaları
farm_thatch_corner|Thatch Roof Corner|茅草屋顶转角|Strohdachecke|Esquina de tejado de paja|Angle de toit de chaume|Angolo del tetto di paglia|Nádtetősarok|わら屋根の角|초가지붕 모서리|Canto do telhado de palha|Угол соломенной крыши|Saz çatı köşesi
farm_thatch_eave|Thatch Eaves|茅草屋檐|Strohdachtraufe|Alero de paja|Avant-toit de chaume|Gronda di paglia|Nádtető eresze|わら屋根の軒|초가지붕 처마|Beiral de palha|Соломенный карниз|Saz saçak
farm_thatch_ridge|Thatch Roof Ridge|茅草屋脊|Strohdachfirst|Caballete de paja|Faîtage de chaume|Colmo del tetto di paglia|Nádtetőgerinc|わら屋根の棟|초가지붕 용마루|Cumeeira de palha|Конёк соломенной крыши|Saz çatı mahyası
farm_thatch_slope|Thatch Roof Slope|茅草屋顶斜面|Strohdachschräge|Pendiente de tejado de paja|Pente de toit de chaume|Falda del tetto di paglia|Nádtető lejtője|わら屋根の斜面|초가지붕 경사면|Inclinação do telhado de palha|Скат соломенной крыши|Saz çatı eğimi
island_parrot_platform|Parrot Express Platform|鹦鹉快递站台|Papageienexpress-Plattform|Plataforma del expreso de loros|Plateforme de l'express perroquet|Piattaforma dell'espresso dei pappagalli|Papagájexpressz-állomás|オウムエクスプレスの乗り場|앵무새 특급 승강장|Plataforma do expresso dos papagaios|Платформа попугайного экспресса|Papağan ekspresi platformu
birdie_hut|Birdie's Hut|贝啼小屋|Birdies Hütte|Cabaña de Birdie|Cabane de Birdie|Capanna di Birdie|Birdie kunyhója|バーディーの小屋|버디의 오두막|Cabana da Birdie|Хижина Берди|Birdie'nin kulübesi
buried_pebble_ring|Pebble Ring|卵石环|Kieselring|Círculo de guijarros|Cercle de galets|Cerchio di ciottoli|Kavicskör|小石の輪|자갈 고리|Círculo de seixos|Кольцо из гальки|Çakıl halkası
buried_round_stone_a|Round Hint Stone|圆形线索石|Runder Hinweisstein|Piedra redonda de pista|Pierre ronde d'indice|Pietra rotonda d'indizio|Kerek nyomjelző kő|丸い手がかりの石|둥근 단서 돌|Pedra redonda de pista|Круглый камень-подсказка|Yuvarlak ipucu taşı
island_hint_white_flowers|Island Hint Flowers|姜岛线索花|Insel-Hinweisblumen|Flores de pista de la isla|Fleurs d'indice de l'île|Fiori d'indizio dell'isola|Szigeti nyomjelző virágok|島の手がかりの花|섬 단서 꽃|Flores de pista da ilha|Островные цветы-подсказки|Ada ipucu çiçekleri
island_sand_arc|Sand Arc Mark|沙地弧形标记|Bogenmarkierung im Sand|Marca de arco en la arena|Arc tracé dans le sable|Segno ad arco sulla sabbia|Homokív-jel|砂の弧の印|모래 호 표식|Marca de arco na areia|Дуга на песке|Kumdaki yay işareti
island_sand_cross|Sand Cross Mark|沙地十字标记|Kreuzmarkierung im Sand|Marca de cruz en la arena|Croix tracée dans le sable|Croce sulla sabbia|Homokkereszt-jel|砂の十字の印|모래 십자 표식|Marca de cruz na areia|Крест на песке|Kumdaki çarpı işareti
island_sand_dots|Sand Dot Marks|沙地点状标记|Punktmarkierung im Sand|Puntos en la arena|Points tracés dans le sable|Punti sulla sabbia|Homokpontok|砂の点の印|모래 점 표식|Pontos na areia|Точки на песке|Kumdaki nokta işaretleri
island_starfish_gold|Island Starfish|姜岛海星|Insel-Seestern|Estrella de mar de la isla|Étoile de mer de l'île|Stella marina dell'isola|Szigeti tengeri csillag|島のヒトデ|섬 불가사리|Estrela-do-mar da ilha|Островная морская звезда|Ada denizyıldızı
captain_cabin_shell|Captain's Cabin|船长船舱|Kapitänskajüte|Camarote del capitán|Cabine du capitaine|Cabina del capitano|Kapitánykabin|船長の船室|선장 선실|Cabine do capitão|Каюта капитана|Kaptan kamarası
crystal_cave_brazier|Crystal Cave Brazier|水晶洞火盆|Feuerbecken der Kristallhöhle|Brasero de la cueva de cristal|Brasero de la grotte de cristal|Braciere della grotta di cristallo|Kristálybarlangi tűztál|水晶洞窟の火鉢|수정 동굴 화로|Braseiro da caverna de cristal|Жаровня кристальной пещеры|Kristal mağarası mangalı
crystal_cave_statue|Crystal Cave Beast Statue|水晶洞怪兽石像|Bestienstatue der Kristallhöhle|Estatua de bestia de la cueva|Statue de bête de la grotte|Statua della bestia della grotta|Kristálybarlangi szörnyszobor|水晶洞窟の獣の石像|수정 동굴 괴수 석상|Estátua da fera da caverna|Статуя зверя кристальной пещеры|Kristal mağarası canavar heykeli
gourmand_cane|Gourmand Frog Cane|美食家青蛙手杖|Gourmand-Froschstock|Bastón de la rana gourmet|Canne de la grenouille gourmande|Bastone della rana buongustaia|Ínyenc béka sétabotja|グルメカエルの杖|미식가 개구리 지팡이|Bengala do sapo gourmet|Трость лягушки-гурмана|Gurme kurbağanın bastonu
gourmand_incense_burner|Gourmand Frog Incense Burner|美食家青蛙香炉|Räuchergefäß des Gourmand-Froschs|Incensario de la rana gourmet|Brûle-encens de la grenouille|Incensiere della rana buongustaia|Ínyenc béka füstölője|グルメカエルの香炉|미식가 개구리 향로|Incensário do sapo gourmet|Курильница лягушки-гурмана|Gurme kurbağanın tütsülüğü
gourmand_woven_mat|Gourmand Frog Woven Mat|美食家青蛙编织席|Flechtmatte des Gourmand-Froschs|Estera de la rana gourmet|Natte de la grenouille gourmande|Stuoia della rana buongustaia|Ínyenc béka gyékénye|グルメカエルの編みマット|미식가 개구리 짜임 매트|Esteira do sapo gourmet|Циновка лягушки-гурмана|Gurme kurbağanın hasırı
sand_duggy_hole|Sand Duggy Hole|沙地掘地怪洞口|Sand-Duggy-Loch|Agujero del Duggy de arena|Trou de Duggy des sables|Buco del Duggy della sabbia|Homoki Duggy ürege|砂のダギーの穴|모래 더기 구멍|Buraco do Duggy da areia|Нора песчаного дагги|Kum Duggy'sinin deliği
island_shipwreck|Island Shipwreck|姜岛沉船|Insel-Schiffswrack|Naufragio de la isla|Épave de l'île|Relitto dell'isola|Szigeti hajóroncs|島の難破船|섬 난파선|Naufrágio da ilha|Островное кораблекрушение|Ada batığı
resort_bar_drinks|Resort Bar Drinks|度假村吧台饮料|Getränke der Ferienbar|Bebidas del bar del resort|Boissons du bar de la station|Bevande del bar del resort|Üdülőbár italai|リゾートバーの飲み物|리조트 바 음료|Bebidas do bar do resort|Напитки курортного бара|Tatil köyü bar içecekleri
resort_carved_bar_counter|Resort Carved Bar Counter|度假村雕花吧台|Geschnitzter Ferienbartresen|Barra tallada del resort|Comptoir sculpté de la station|Bancone intagliato del resort|Faragott üdülőbárpult|リゾートの彫刻カウンター|리조트 조각 바 카운터|Balcão entalhado do resort|Резная стойка курортного бара|Tatil köyü oymalı bar tezgâhı
resort_changing_entry|Resort Changing Room Entrance|度假村更衣室入口|Umkleideeingang der Ferienanlage|Entrada del vestuario del resort|Entrée du vestiaire de la station|Ingresso dello spogliatoio del resort|Üdülőöltöző bejárata|リゾート更衣室の入口|리조트 탈의실 입구|Entrada do vestiário do resort|Вход в курортную раздевалку|Tatil köyü soyunma odası girişi
resort_leaf_wrapped_column|Resort Leaf-Wrapped Column|度假村缠叶柱|Blattumwickelte Feriensäule|Columna con hojas del resort|Colonne feuillue de la station|Colonna avvolta di foglie|Levéllel borított üdülőoszlop|リゾートの葉巻き柱|리조트 잎 장식 기둥|Coluna envolta em folhas|Курортная колонна с листьями|Yaprak sarılı tatil köyü sütunu
resort_ruined_column|Resort Column|度假村立柱|Feriensäule|Columna del resort|Colonne de la station|Colonna del resort|Üdülőoszlop|リゾートの柱|리조트 기둥|Coluna do resort|Курортная колонна|Tatil köyü sütunu
resort_tile_roof_high|Resort High Roof Section|度假村屋顶高段|Hohes Feriendachsegment|Sección alta del tejado|Section haute du toit|Sezione alta del tetto|Üdülőtető magas eleme|リゾート屋根の高い部分|리조트 지붕 높은 부분|Seção alta do telhado|Высокая секция курортной крыши|Tatil köyü yüksek çatı bölümü
resort_tile_roof_low|Resort Low Roof Section|度假村屋顶低段|Niedriges Feriendachsegment|Sección baja del tejado|Section basse du toit|Sezione bassa del tetto|Üdülőtető alacsony eleme|リゾート屋根の低い部分|리조트 지붕 낮은 부분|Seção baixa do telhado|Низкая секция курортной крыши|Tatil köyü alçak çatı bölümü
resort_tile_roof_mid|Resort Middle Roof Section|度假村屋顶中段|Mittleres Feriendachsegment|Sección central del tejado|Section centrale du toit|Sezione centrale del tetto|Üdülőtető középső eleme|リゾート屋根の中間部分|리조트 지붕 중간 부분|Seção central do telhado|Средняя секция курортной крыши|Tatil köyü orta çatı bölümü
resort_tile_roof_ridge|Resort Roof Ridge|度假村屋脊|Feriendachfirst|Caballete del resort|Faîtage de la station|Colmo del tetto del resort|Üdülőtetőgerinc|リゾート屋根の棟|리조트 용마루|Cumeeira do resort|Конёк курортной крыши|Tatil köyü çatı mahyası
resort_tile_roof_wide_crown|Resort Wide Roof Crown|度假村宽顶冠|Breite Feriendachkrone|Remate ancho del tejado|Couronnement large du toit|Coronamento largo del tetto|Üdülőtető széles koronája|リゾート屋根の広い頂部|리조트 넓은 지붕 마루|Coroamento largo do telhado|Широкая вершина курортной крыши|Tatil köyü geniş çatı tepesi
resort_roof|Resort Roof|度假村整装屋顶|Feriendach|Tejado del resort|Toit de la station|Tetto del resort|Üdülőtető|リゾートの屋根|리조트 지붕|Telhado do resort|Крыша курорта|Tatil köyü çatısı
resort_beach_chair|Resort Beach Chair|度假村躺椅|Ferienstrandliege|Tumbona del resort|Chaise longue de la station|Sdraio del resort|Üdülőnyugágy|リゾートのビーチチェア|리조트 비치 의자|Espreguiçadeira do resort|Курортный шезлонг|Tatil köyü şezlongu
resort_opening_notice|Resort Opening Notice|度假村营业告示|Öffnungsschild der Ferienanlage|Aviso de apertura del resort|Avis d'ouverture de la station|Avviso di apertura del resort|Üdülő nyitvatartási tábla|リゾートの営業案内|리조트 영업 안내판|Aviso de abertura do resort|Объявление об открытии курорта|Tatil köyü açılış duyurusu
resort_towel_green|Resort Beach Towel|度假村沙滩毛巾|Ferienstrandtuch|Toalla de playa del resort|Serviette de plage de la station|Telo da spiaggia del resort|Üdülő strandtörölköző|リゾートのビーチタオル|리조트 비치 타월|Toalha de praia do resort|Курортное пляжное полотенце|Tatil köyü plaj havlusu
resort_umbrella_coral|Resort Beach Umbrella|度假村遮阳伞|Ferienstrandschirm|Sombrilla del resort|Parasol de la station|Ombrellone del resort|Üdülő napernyő|リゾートのパラソル|리조트 파라솔|Guarda-sol do resort|Курортный пляжный зонт|Tatil köyü plaj şemsiyesi
mermaid_performance_rock|Mermaid Performance Rock|美人鱼表演礁石|Meerjungfrauen-Bühnenfelsen|Roca de actuación de la sirena|Rocher de spectacle de la sirène|Roccia dello spettacolo della sirena|Sellő előadóhelyének sziklája|人魚の演奏岩|인어 공연 바위|Rocha de apresentação da sereia|Скала выступления русалки|Denizkızı gösteri kayası
pirate_bar_assembly|Pirate Bar Counter|海盗吧台|Piratentresen|Barra de piratas|Comptoir des pirates|Bancone dei pirati|Kalózbárpult|海賊のバーカウンター|해적 바 카운터|Balcão dos piratas|Пиратская барная стойка|Korsan bar tezgâhı
pirate_square_stool|Pirate Stool|海盗方凳|Piratenhocker|Taburete pirata|Tabouret de pirate|Sgabello dei pirati|Kalózsámli|海賊のスツール|해적 사각 의자|Banquinho dos piratas|Пиратский табурет|Korsan taburesi
pirate_barrel_dark|Dark Pirate Barrel|深色海盗木桶|Dunkles Piratenfass|Barril pirata oscuro|Tonneau de pirate foncé|Barile scuro dei pirati|Sötét kalózhordó|海賊の濃色樽|해적 짙은 나무통|Barril escuro dos piratas|Тёмная пиратская бочка|Koyu korsan fıçısı
pirate_cannon|Pirate Cannon|海盗火炮|Piratenkanone|Cañón pirata|Canon de pirate|Cannone dei pirati|Kalózágyú|海賊の大砲|해적 대포|Canhão dos piratas|Пиратская пушка|Korsan topu
pirate_dartboard|Pirate Dartboard|海盗飞镖盘|Piraten-Dartscheibe|Diana pirata|Cible de fléchettes des pirates|Bersaglio per freccette dei pirati|Kalóz dartstábla|海賊のダーツ盤|해적 다트판|Alvo de dardos dos piratas|Пиратская мишень для дартса|Korsan dart tahtası
pirate_skull_flag|Pirate Skull Flag|海盗骷髅旗|Piraten-Totenkopfflagge|Bandera pirata con calavera|Drapeau pirate à tête de mort|Bandiera pirata con teschio|Kalóz koponyás zászló|海賊のドクロ旗|해적 해골 깃발|Bandeira pirata de caveira|Пиратский флаг с черепом|Korsan kurukafa bayrağı
pirate_palm_planter_teal|Pirate Palm Planter|海盗棕榈盆栽|Piraten-Palmenkübel|Maceta pirata de palmera|Pot de palmier des pirates|Vaso di palma dei pirati|Kalóz pálmacserép|海賊のヤシ鉢|해적 야자 화분|Vaso de palmeira dos piratas|Пиратская пальма в горшке|Korsan palmiye saksısı
pirate_round_table|Pirate Round Table|海盗圆桌|Piraten-Rundtisch|Mesa redonda pirata|Table ronde des pirates|Tavolo rotondo dei pirati|Kalóz kerek asztal|海賊の丸テーブル|해적 원탁|Mesa redonda dos piratas|Пиратский круглый стол|Korsan yuvarlak masası
pirate_cove_rowboat|Pirate Cove Rowboat|海盗湾划艇|Ruderboot der Piratenbucht|Bote de la cala pirata|Canot de la crique des pirates|Barca della baia dei pirati|Kalózöböl evezős csónakja|海賊の入り江の手こぎ舟|해적 동굴 노젓는 배|Barco da enseada dos piratas|Лодка пиратской бухты|Korsan koyu kürekli teknesi
pirate_purple_rug|Pirate Purple Rug|海盗紫色地毯|Violetter Piratenteppich|Alfombra pirata morada|Tapis pirate violet|Tappeto viola dei pirati|Lila kalózszőnyeg|海賊の紫のラグ|해적 보라색 러그|Tapete roxo dos piratas|Фиолетовый пиратский ковёр|Mor korsan halısı
pirate_treasure_table|Pirate Treasure Table|海盗宝物桌|Piraten-Schatztisch|Mesa de tesoros pirata|Table aux trésors des pirates|Tavolo del tesoro dei pirati|Kalóz kincses asztal|海賊の宝物テーブル|해적 보물 탁자|Mesa de tesouros dos piratas|Пиратский стол с сокровищами|Korsan hazine masası
island_sand_starfish|Sand Starfish|沙地海星|Sand-Seestern|Estrella de mar de arena|Étoile de mer des sables|Stella marina sulla sabbia|Homoki tengeri csillag|砂浜のヒトデ|모래 불가사리|Estrela-do-mar da areia|Морская звезда на песке|Kum denizyıldızı
volcano_bone_spike|Volcano Bone Spike|火山骨刺|Vulkan-Knochenspitze|Púa ósea del volcán|Pointe d'os du volcan|Spina ossea del vulcano|Vulkáni csonttüske|火山の骨のとげ|화산 뼈 가시|Espinho de osso do vulcão|Костяной шип вулкана|Volkan kemik dikeni
volcano_dragon_skull|Volcano Dragon Skull|火山龙头骨|Vulkan-Drachenschädel|Cráneo de dragón del volcán|Crâne de dragon du volcan|Teschio di drago del vulcano|Vulkáni sárkánykoponya|火山のドラゴンの頭骨|화산 용 두개골|Crânio de dragão do vulcão|Драконий череп вулкана|Volkan ejderha kafatası
volcano_femur_in_rock|Volcano Bone in Rock|火山嵌岩腿骨|Vulkanknochen im Fels|Hueso incrustado del volcán|Os enchâssé du volcan|Osso nella roccia del vulcano|Sziklába ágyazott vulkáni csont|火山の岩に埋まった骨|화산 암석 속 다리뼈|Osso na rocha do vulcão|Кость в вулканической скале|Volkan kayasındaki kemik
volcano_rib_arch|Volcano Rib Arch|火山肋骨拱|Vulkan-Rippenbogen|Arco de costillas del volcán|Arche de côtes du volcan|Arco di costole del vulcano|Vulkáni bordaív|火山の肋骨アーチ|화산 갈비뼈 아치|Arco de costelas do vulcão|Арка из рёбер вулкана|Volkan kaburga kemeri
caldera_forge|Caldera Forge|火山口锻造台|Krater-Schmiede|Forja de la caldera|Forge de la caldeira|Forgia della caldera|Kráterkovácsműhely|火口の鍛冶場|분화구 대장간|Forja da caldeira|Кузница кальдеры|Krater demir ocağı
caldera_monument|Caldera Monument|火山口完美雕刻|Krater-Denkmal|Monumento de la caldera|Monument de la caldeira|Monumento della caldera|Kráteremlékmű|火口の記念碑|분화구 기념비|Monumento da caldeira|Монумент кальдеры|Krater anıtı
volcano_canister_174|Volcano Canister|火山罐|Vulkanbehälter|Vasija del volcán|Récipient du volcan|Contenitore del vulcano|Vulkáni edény|火山のつぼ|화산 항아리|Recipiente do vulcão|Сосуд вулкана|Volkan kabı
volcano_dwarf_cabinet|Dwarf Shop Cabinet|矮人商店柜|Zwergenladen-Schrank|Armario de la tienda enana|Armoire de la boutique naine|Armadio del negozio dei nani|Törpebolt szekrénye|ドワーフの店の戸棚|드워프 상점 장식장|Armário da loja dos anões|Шкаф лавки гнома|Cüce dükkânı dolabı
volcano_dwarf_counter|Dwarf Shop Counter|矮人商店柜台|Zwergenladen-Tresen|Mostrador de la tienda enana|Comptoir de la boutique naine|Bancone del negozio dei nani|Törpebolt pultja|ドワーフの店のカウンター|드워프 상점 카운터|Balcão da loja dos anões|Прилавок лавки гнома|Cüce dükkânı tezgâhı
volcano_dwarf_display_table|Dwarf Shop Display Table|矮人商店陈列桌|Zwergenladen-Ausstellungstisch|Mesa de exposición enana|Table d'exposition naine|Espositore del negozio dei nani|Törpebolt kiállítóasztala|ドワーフの店の展示台|드워프 상점 진열대|Mesa de exposição dos anões|Витрина лавки гнома|Cüce dükkânı sergileme masası
volcano_dwarf_lantern|Dwarf Shop Lantern|矮人商店灯笼|Zwergenladen-Laterne|Farol de la tienda enana|Lanterne de la boutique naine|Lanterna del negozio dei nani|Törpebolt lámpása|ドワーフの店のランタン|드워프 상점 랜턴|Lanterna da loja dos anões|Фонарь лавки гнома|Cüce dükkânı feneri
volcano_dwarf_slab|Dwarf Shop Stone Slab|矮人商店石台|Zwergenladen-Steinplatte|Losa de la tienda enana|Dalle de la boutique naine|Lastra del negozio dei nani|Törpebolt kőlapja|ドワーフの店の石台|드워프 상점 석판|Laje da loja dos anões|Каменная плита лавки гнома|Cüce dükkânı taş levhası
volcano_floor_switch|Volcano Floor Switch|火山地面开关|Vulkan-Bodenschalter|Interruptor de suelo del volcán|Interrupteur au sol du volcan|Interruttore a pavimento del vulcano|Vulkáni padlókapcsoló|火山の床スイッチ|화산 바닥 스위치|Interruptor de piso do vulcão|Напольный переключатель вулкана|Volkan zemin düğmesi
volcano_parrot_perch|Volcano Parrot Perch|火山鹦鹉栖木|Vulkan-Papageiensitz|Posadero de loro del volcán|Perchoir à perroquet du volcan|Posatoio del pappagallo del vulcano|Vulkáni papagájülőke|火山のオウムの止まり木|화산 앵무새 횃대|Poleiro de papagaio do vulcão|Жёрдочка попугая вулкана|Volkan papağan tüneği
volcano_shortcut_hole|Volcano Shortcut Hole|火山捷径洞|Vulkan-Abkürzungsloch|Agujero de atajo del volcán|Trou du raccourci du volcan|Buco della scorciatoia del vulcano|Vulkáni rövidítőjárat|火山の近道の穴|화산 지름길 구멍|Buraco de atalho do vulcão|Отверстие короткого пути вулкана|Volkan kestirme deliği
volcano_spout_dry|Volcano Water Spout|火山出水口|Vulkan-Wasserauslass|Salida de agua del volcán|Bouche d'eau du volcan|Bocca d'acqua del vulcano|Vulkáni vízkifolyó|火山の給水口|화산 급수구|Bica de água do vulcão|Водосток вулкана|Volkan su ağzı
volcano_well_pipe|Volcano Well Pipe|火山水井管|Vulkan-Brunnenrohr|Tubería del pozo del volcán|Conduite du puits du volcan|Tubo del pozzo del vulcano|Vulkáni kút csöve|火山の井戸の管|화산 우물 파이프|Cano do poço do vulcão|Труба колодца вулкана|Volkan kuyu borusu
volcano_big_gear|Large Volcano Gear|火山大齿轮|Großes Vulkan-Zahnrad|Engranaje grande del volcán|Grand engrenage du volcan|Grande ingranaggio del vulcano|Nagy vulkáni fogaskerék|火山の大きな歯車|화산 큰 톱니바퀴|Engrenagem grande do vulcão|Большая шестерня вулкана|Büyük volkan dişlisi
volcano_bolt|Volcano Bolt|火山螺栓|Vulkanbolzen|Perno del volcán|Boulon du volcan|Bullone del vulcano|Vulkáni csavar|火山のボルト|화산 볼트|Parafuso do vulcão|Болт вулкана|Volkan cıvatası
volcano_broken_pillar|Volcano Broken Pillar|火山残柱|Gebrochene Vulkansäule|Pilar roto del volcán|Pilier brisé du volcan|Pilastro rotto del vulcano|Törött vulkáni oszlop|火山の折れた柱|화산 부러진 기둥|Pilar quebrado do vulcão|Сломанная колонна вулкана|Kırık volkan sütunu
volcano_floor_hatch|Volcano Floor Hatch|火山地板井盖|Vulkan-Bodenluke|Trampilla del suelo del volcán|Trappe au sol du volcan|Botola del vulcano|Vulkáni padlónyílás|火山の床ハッチ|화산 바닥 덮개|Alçapão do vulcão|Напольный люк вулкана|Volkan zemin kapağı
volcano_medium_gear|Medium Volcano Gear|火山中齿轮|Mittleres Vulkan-Zahnrad|Engranaje mediano del volcán|Engrenage moyen du volcan|Ingranaggio medio del vulcano|Közepes vulkáni fogaskerék|火山の中くらいの歯車|화산 중간 톱니바퀴|Engrenagem média do vulcão|Средняя шестерня вулкана|Orta volkan dişlisi
volcano_rubble|Volcano Rubble|火山碎石堆|Vulkanschutt|Escombros del volcán|Gravats du volcan|Macerie del vulcano|Vulkáni törmelék|火山のがれき|화산 잔해 더미|Escombros do vulcão|Обломки вулкана|Volkan molozu
volcano_small_gear|Small Volcano Gear|火山小齿轮|Kleines Vulkan-Zahnrad|Engranaje pequeño del volcán|Petit engrenage du volcan|Piccolo ingranaggio del vulcano|Kis vulkáni fogaskerék|火山の小さな歯車|화산 작은 톱니바퀴|Engrenagem pequena do vulcão|Маленькая шестерня вулкана|Küçük volkan dişlisi
volcano_vent_brick|Volcano Vent Brick|火山通风砖|Vulkan-Lüftungsziegel|Ladrillo de ventilación del volcán|Brique de ventilation du volcan|Mattone di ventilazione del vulcano|Vulkáni szellőzőtégla|火山の通気レンガ|화산 통풍 벽돌|Tijolo de ventilação do vulcão|Вентиляционный кирпич вулкана|Volkan havalandırma tuğlası
qi_challenge_board|Qi's Challenge Board|齐先生挑战板|Qis Herausforderungstafel|Tablero de desafíos de Qi|Tableau des défis de Qi|Bacheca delle sfide di Qi|Qi kihívástáblája|ミスターQiのチャレンジボード|치의 도전 게시판|Quadro de desafios do Qi|Доска испытаний Ци|Qi'nin meydan okuma panosu
qi_computer_desk|Qi's Computer Desk|齐先生电脑桌|Qis Computertisch|Mesa de ordenador de Qi|Bureau informatique de Qi|Scrivania del computer di Qi|Qi számítógépasztala|ミスターQiのパソコンデスク|치의 컴퓨터 책상|Mesa de computador do Qi|Компьютерный стол Ци|Qi'nin bilgisayar masası
qi_dropbox|Qi's Delivery Box|齐先生交付箱|Qis Abgabekiste|Caja de entrega de Qi|Boîte de livraison de Qi|Cassetta delle consegne di Qi|Qi leadóládája|ミスターQiの納品箱|치의 납품 상자|Caixa de entrega do Qi|Ящик сдачи Ци|Qi'nin teslimat kutusu
palm_wall_ornament_left|Palm Wall Ornament (Left)|棕榈墙饰（左）|Palmen-Wanddeko (links)|Adorno de palmera (izquierda)|Décoration murale de palmier (gauche)|Decorazione di palma (sinistra)|Pálma falidísz (bal)|ヤシの壁飾り（左）|야자 벽 장식 (왼쪽)|Enfeite de palmeira (esquerda)|Настенная пальма (левая)|Palmiye duvar süsü (sol)
palm_wall_ornament_right|Palm Wall Ornament (Right)|棕榈墙饰（右）|Palmen-Wanddeko (rechts)|Adorno de palmera (derecha)|Décoration murale de palmier (droite)|Decorazione di palma (destra)|Pálma falidísz (jobb)|ヤシの壁飾り（右）|야자 벽 장식 (오른쪽)|Enfeite de palmeira (direita)|Настенная пальма (правая)|Palmiye duvar süsü (sağ)
island_beached_seaweed|Beached Seaweed|搁浅海藻|Angespülter Seetang|Algas varadas|Algues échouées|Alghe spiaggiate|Partra vetett hínár|打ち上げられた海藻|떠밀려 온 해초|Algas encalhadas|Выброшенные водоросли|Kıyıya vurmuş yosun
island_shell_clam|Decorative Clam Shell|装饰蛤蜊壳|Dekorative Venusmuschel|Concha de almeja decorativa|Coquille de palourde décorative|Guscio di vongola decorativo|Dísz kagylóhéj|飾りのアサリの貝殻|장식 조개껍데기|Concha decorativa de amêijoa|Декоративная раковина моллюска|Dekoratif kum midyesi kabuğu
island_shell_cockle|Decorative Cockle Shell|装饰鸟蛤壳|Dekorative Herzmuschel|Concha de berberecho decorativa|Coquille de coque décorative|Guscio di cuore decorativo|Dísz szívkagylóhéj|飾りのザルガイの貝殻|장식 새조개껍데기|Concha decorativa de berbigão|Декоративная сердцевидка|Dekoratif kalp midyesi kabuğu
island_bright_longleaf_assembly|Tropical Longleaf Plant|热带长叶植株|Tropische Langblattpflanze|Planta tropical de hojas largas|Plante tropicale à longues feuilles|Pianta tropicale a foglie lunghe|Hosszú levelű trópusi növény|熱帯の長葉植物|열대 긴잎 식물|Planta tropical de folhas longas|Тропическое длиннолистное растение|Uzun yapraklı tropik bitki
island_counting_purple_flowers|Island Survey Purple Flowers|姜岛调查紫花|Violette Insel-Untersuchungsblumen|Flores moradas de investigación|Fleurs violettes d'enquête|Fiori viola dell'indagine|Szigeti felmérés lila virágai|島の調査用の紫の花|섬 조사용 보라색 꽃|Flores roxas da pesquisa da ilha|Фиолетовые цветы для исследования|Ada araştırması mor çiçekleri
island_broadleaf_rosette|Tropical Broadleaf Rosette|热带阔叶丛|Tropische Breitblattrosette|Roseta tropical de hojas anchas|Rosette tropicale à larges feuilles|Rosetta tropicale a foglie larghe|Széles levelű trópusi tőlevélrózsa|熱帯の広葉ロゼット|열대 넓은잎 식물|Roseta tropical de folhas largas|Тропическая широколистная розетка|Geniş yapraklı tropik rozet
island_curled_fern|Tropical Curled Fern|热带卷叶蕨|Tropischer Rollfarn|Helecho tropical rizado|Fougère tropicale enroulée|Felce tropicale arricciata|Trópusi kunkorodó páfrány|熱帯の巻き葉シダ|열대 말린 고사리|Samambaia tropical enrolada|Тропический кудрявый папоротник|Kıvrık tropik eğrelti
volcano_floor|Volcano Floor|火山地板|Vulkanboden|Suelo del volcán|Sol du volcan|Pavimento del vulcano|Vulkáni padló|火山の床|화산 바닥|Piso do vulcão|Пол вулкана|Volkan zemini
volcano_wall|Volcano Rock Wall|火山岩壁|Vulkanfelswand|Pared rocosa del volcán|Paroi rocheuse du volcan|Parete rocciosa del vulcano|Vulkáni sziklafal|火山の岩壁|화산 암벽|Parede rochosa do vulcão|Скальная стена вулкана|Volkan kaya duvarı
volcano_mud|Volcano Mud|火山泥|Vulkanschlamm|Barro del volcán|Boue du volcan|Fango del vulcano|Vulkáni sár|火山の泥|화산 진흙|Lama do vulcão|Грязь вулкана|Volkan çamuru
caldera_floor|Caldera Floor|火山口地板|Kraterboden|Suelo de la caldera|Sol de la caldeira|Pavimento della caldera|Kráterpadló|火口の床|분화구 바닥|Piso da caldeira|Пол кальдеры|Krater zemini
caldera_wall|Caldera Rock Wall|火山口岩壁|Kraterfelswand|Pared rocosa de la caldera|Paroi de la caldeira|Parete rocciosa della caldera|Krátersziklafal|火口の岩壁|분화구 암벽|Parede rochosa da caldeira|Стена кальдеры|Krater kaya duvarı
dwarf_brick|Dwarven Stone Brick|矮人石砖|Zwergensteinziegel|Ladrillo de piedra enano|Brique de pierre naine|Mattone di pietra nanico|Törpe kőtégla|ドワーフの石レンガ|드워프 석재 벽돌|Tijolo de pedra dos anões|Каменный кирпич гномов|Cüce taş tuğlası
dwarf_capstone|Dwarven Gate Capstone|矮人门楣石|Zwergentor-Deckstein|Dintel de puerta enana|Linteau de porte naine|Architrave della porta nanica|Törpekapu szemöldökköve|ドワーフ門のまぐさ石|드워프 문 상인방|Verga do portão dos anões|Перемычка ворот гномов|Cüce kapısı üst taşı
volcano_bridge_deck|Volcano Bridge Deck|火山桥面|Vulkanbrückendeck|Tablero del puente del volcán|Tablier du pont du volcan|Piano del ponte del vulcano|Vulkáni hídpálya|火山の橋床|화산 다리 바닥|Tabuleiro da ponte do vulcão|Настил моста вулкана|Volkan köprü tabliyesi
volcano_bridge_rail|Volcano Bridge Railing|火山桥栏|Vulkanbrückengeländer|Barandilla del puente del volcán|Garde-corps du pont du volcan|Ringhiera del ponte del vulcano|Vulkáni hídkorlát|火山の橋の手すり|화산 다리 난간|Corrimão da ponte do vulcão|Перила моста вулкана|Volkan köprü korkuluğu
qi_room_tile|Qi Room Floor Tile|齐先生房间地砖|Bodenfliese von Qis Zimmer|Baldosa de la sala de Qi|Carreau de la salle de Qi|Piastrella della stanza di Qi|Qi szobájának padlólapja|ミスターQiの部屋の床タイル|치 방 바닥 타일|Ladrilho da sala do Qi|Плитка комнаты Ци|Qi odası yer karosu
volcano_cooled_lava|Cooled Lava|冷却熔岩|Abgekühlte Lava|Lava enfriada|Lave refroidie|Lava raffreddata|Lehűlt láva|冷えた溶岩|식은 용암|Lava resfriada|Остывшая лава|Soğumuş lav
dig_site_bridge|Dig Site Bridge|挖掘场木板桥|Ausgrabungsbrücke|Puente de excavación|Pont du site de fouilles|Ponte del sito di scavo|Ásatási híd|発掘場の橋|발굴지 다리|Ponte do local de escavação|Мост раскопок|Kazı alanı köprüsü
'''


def localizations(definitions):
    rows = {}
    for line in ROWS.strip().splitlines():
        values = line.split('|')
        assert len(values) == len(LANGUAGES) + 1, values[0]
        assert values[0] not in rows
        rows[values[0]] = values[1:]
    assert all(len(names) == len(LANGUAGES) for names in ENTITY_NAMES.values())
    result = {language: {'entity.stardewcraft.' + entity: names[i]
                        for entity, names in ENTITY_NAMES.items()}
              for i, language in enumerate(LANGUAGES)}
    originals = {}
    for definition in definitions:
        name = definition['id'].removeprefix('ginger_')
        key = 'block.stardewcraft.' + definition['id']
        for i, language in enumerate(LANGUAGES):
            if name in SOURCE_NAMES:
                book, original_key = SOURCE_NAMES[name]
                suffix = '.' + LOCALES[i] if LOCALES[i] else ''
                path = ROOT / '源文件/Content/Strings' / (book + suffix + '.json')
                if path not in originals: originals[path] = json.loads(path.read_text())
                text = originals[path][original_key]
            else:
                assert name in rows, 'Review a localized object name before registration: ' + name
                text = rows[name][i]
            assert text.strip(), (language, name)
            result[language][key] = text
    return result
