package com.stardew.craft.client.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Direct Java port of Stardew Valley's {@code Dialogue.randomName()}. */
final class SdvAnimalNameGenerator {
    private static final String[] JAPANESE_NAMES = {
        "ローゼン", "ミルド", "ココ", "ナミ", "こころ", "サルコ", "ハンゾー", "クッキー", "ココナツ", "せん",
        "ハル", "ラン", "オサム", "ヨシ", "ソラ", "ホシ", "まこと", "マサ", "ナナ", "リオ",
        "リン", "フジ", "うどん", "ミント", "さくら", "ボンボン", "レオ", "モリ", "コーヒー", "ミルク",
        "マロン", "クルミ", "サムライ", "カミ", "ゴロ", "マル", "チビ", "ユキダマ"
    };

    private static final String[] CHINESE_NAMES = {
        "雨果", "蛋挞", "小百合", "毛毛", "小雨", "小溪", "精灵", "安琪儿", "小糕", "玫瑰",
        "小黄", "晓雨", "阿江", "铃铛", "马琪", "果粒", "郁金香", "小黑", "雨露", "小江",
        "灵力", "萝拉", "豆豆", "小莲", "斑点", "小雾", "阿川", "丽丹", "玛雅", "阿豆",
        "花花", "琉璃", "滴答", "阿山", "丹麦", "梅西", "橙子", "花儿", "晓璃", "小夕",
        "山大", "咪咪", "卡米", "红豆", "花朵", "洋洋", "太阳", "小岩", "汪汪", "玛利亚",
        "小菜", "花瓣", "阳阳", "小夏", "石头", "阿狗", "邱洁", "苹果", "梨花", "小希",
        "天天", "浪子", "阿猫", "艾薇儿", "雪梨", "桃花", "阿喜", "云朵", "风儿", "狮子",
        "绮丽", "雪莉", "樱花", "小喜", "朵朵", "田田", "小红", "宝娜", "梅子", "小樱",
        "嘻嘻", "云儿", "小草", "小黄", "纳香", "阿梅", "茶花", "哈哈", "芸儿", "东东",
        "小羽", "哈豆", "桃子", "茶叶", "双双", "沫沫", "楠楠", "小爱", "麦当娜", "杏仁",
        "椰子", "小王", "泡泡", "小林", "小灰", "马格", "鱼蛋", "小叶", "小李", "晨晨",
        "小琳", "小慧", "布鲁", "晓梅", "绿叶", "甜豆", "小雪", "晓林", "康康", "安妮",
        "樱桃", "香板", "甜甜", "雪花", "虹儿", "美美", "葡萄", "薇儿", "金豆", "雪玲",
        "瑶瑶", "龙眼", "丁香", "晓云", "雪豆", "琪琪", "麦子", "糖果", "雪丽", "小艺",
        "小麦", "小圆", "雨佳", "小火", "麦茶", "圆圆", "春儿", "火灵", "板子", "黑点",
        "冬冬", "火花", "米粒", "喇叭", "晓秋", "跟屁虫", "米果", "欢欢", "爱心", "松子",
        "丫头", "双子", "豆芽", "小子", "彤彤", "棉花糖", "阿贵", "仙儿", "冰淇淋", "小彬",
        "贤儿", "冰棒", "仔仔", "格子", "水果", "悠悠", "莹莹", "巧克力", "梦洁", "汤圆",
        "静香", "茄子", "珍珠"
    };

    private static final String[] RUSSIAN_NAMES = {
        "Августина", "Альф", "Анфиса", "Ариша", "Афоня", "Баламут", "Балкан", "Бандит", "Бланка", "Бобик",
        "Боня", "Борька", "Буренка", "Бусинка", "Вася", "Гаврюша", "Глаша", "Гоша", "Дуня", "Дуся",
        "Зорька", "Ивонна", "Игнат", "Кеша", "Клара", "Кузя", "Лада", "Максимус", "Маня", "Марта",
        "Маруся", "Моня", "Мотя", "Мурзик", "Мурка", "Нафаня", "Ника", "Нюша", "Проша", "Пятнушка",
        "Сеня", "Сивка", "Тихон", "Тоша", "Фунтик", "Шайтан", "Юнона", "Юпитер", "Ягодка", "Яшка"
    };

    private static final String[] STARTING_CONSONANTS = {
        "B", "Br", "J", "F", "S", "M", "C", "Ch", "L", "P", "K", "W",
        "G", "Z", "Tr", "T", "Gr", "Fr", "Pr", "N", "Sn", "R", "Sh", "St"
    };
    private static final String[] CONSONANTS = {
        "ll", "tch", "l", "m", "n", "p", "r", "s", "t", "c", "rt", "ts"
    };
    private static final String[] VOWELS = {"a", "e", "i", "o", "u"};
    private static final String[] CONSONANT_ENDINGS = {"ie", "o", "a", "ers", "ley"};
    private static final Map<String, String[]> ENDINGS = Map.of(
        "a", new String[]{"nie", "bell", "bo", "boo", "bella", "s"},
        "e", new String[]{"ll", "llo", "", "o"},
        "i", new String[]{"ck", "e", "bo", "ba", "lo", "la", "to", "ta", "no", "na", "ni", "a", "o", "zor", "que", "ca", "co", "mi"},
        "o", new String[]{"nie", "ze", "dy", "da", "o", "ver", "la", "lo", "s", "ny", "mo", "ra"},
        "u", new String[]{"rt", "mo", "", "s"}
    );
    private static final Map<String, String[]> SHORT_ENDINGS = Map.of(
        "a", new String[]{"nny", "sper", "trina", "bo", "-bell", "boo", "lbert", "sko", "sh", "ck", "ishe", "rk"},
        "e", new String[]{"lla", "llo", "rnard", "cardo", "ffe", "ppo", "ppa", "tch", "x"},
        "i", new String[]{"llard", "lly", "lbo", "cky", "card", "ne", "nnie", "lbert", "nono", "nano", "nana", "ana", "nsy", "msy", "skers", "rdo", "rda", "sh"},
        "o", new String[]{"nie", "zzy", "do", "na", "la", "la", "ver", "ng", "ngus", "ny", "-mo", "llo", "ze", "ra", "ma", "cco", "z"},
        "u", new String[]{"ssie", "bbie", "ffy", "bba", "rt", "s", "mby", "mbo", "mbus", "ngus", "cky"}
    );

    private SdvAnimalNameGenerator() {
    }

    static String randomName(String languageCode, Random random) {
        String language = languageCode == null ? "" : languageCode.toLowerCase(Locale.ROOT);
        if (language.startsWith("ja")) return choose(random, JAPANESE_NAMES);
        if (language.startsWith("zh")) return choose(random, CHINESE_NAMES);
        if (language.startsWith("ru")) return choose(random, RUSSIAN_NAMES);

        int nameLength = 3 + random.nextInt(3);
        // The original deliberately passes Length - 1, so "St" is excluded.
        String name = STARTING_CONSONANTS[random.nextInt(STARTING_CONSONANTS.length - 1)];
        for (int i = 1; i < nameLength - 1; i++) {
            name += i % 2 != 0 ? choose(random, VOWELS) : choose(random, CONSONANTS);
            if (name.length() >= nameLength) break;
        }

        String lastLetter = name.substring(name.length() - 1);
        if (random.nextBoolean() && !isVowel(lastLetter)) {
            name += choose(random, CONSONANT_ENDINGS);
        } else if (isVowel(lastLetter)) {
            if (random.nextDouble() < 0.8D) {
                name += choose(random, (name.length() > 3 ? ENDINGS : SHORT_ENDINGS).get(lastLetter));
            }
        } else {
            name += choose(random, VOWELS);
        }

        for (int i = name.length() - 1; i > 2; i--) {
            if (!isVowel(name.substring(i, i + 1)) || !isVowel(name.substring(i - 2, i - 1))) continue;
            switch (name.charAt(i - 1)) {
                case 'c' -> {
                    name = name.substring(0, i) + "k" + name.substring(i);
                    i--;
                }
                case 'r' -> {
                    name = name.substring(0, i - 1) + "k" + name.substring(i);
                    i--;
                }
                case 'l' -> {
                    name = name.substring(0, i - 1) + "n" + name.substring(i);
                    i--;
                }
                default -> {
                }
            }
        }
        if (name.length() <= 3 && random.nextDouble() < 0.1D) {
            name += random.nextBoolean() ? name : "-" + name;
        }
        if (name.length() <= 2 && name.endsWith("e")) {
            name += choose(random, new String[]{"m", "p", "b"});
        }
        return replaceBadRandomName(name, random);
    }

    private static boolean isVowel(String value) {
        for (String vowel : VOWELS) {
            if (vowel.equals(value)) return true;
        }
        return false;
    }

    private static String replaceBadRandomName(String name, Random random) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        String[] blocked = {
            "bitch", "cock", "cum", "fuck", "goock", "gook", "kike", "nigg", "pusie", "puss",
            "puta", "rape", "sex", "shart", "shit", "taboo", "trann", "willy"
        };
        for (String value : blocked) {
            if (lowerName.contains(value)) return random.nextBoolean() ? "Bobo" : "Wumbus";
        }
        return switch (lowerName) {
            case "boner", "boners" -> "Boneo";
            case "bussie" -> "Busu";
            case "cucka", "cucke", "cucko", "cucky", "cuckas", "cuckie", "cuckos", "cuckers" -> "Cubbie";
            case "grope", "gropers" -> "Gropello";
            case "natsi" -> "Natsia";
            case "packi", "packie" -> "Packina";
            case "penos", "penus" -> "Penono";
            case "rapie" -> "Rapimi";
            case "trapi", "trani", "tranie", "trapie", "trananie" -> "Tranello";
            default -> name;
        };
    }

    private static String choose(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }
}
