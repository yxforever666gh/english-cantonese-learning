package com.example.englishcantoneselearning.model

enum class MiniMaxVoiceKind {
    SYSTEM,
    CLONED,
    DESIGNED,
    CUSTOM_FAVORITE,
    UNKNOWN,
}

data class MiniMaxVoice(
    val id: String,
    val name: String,
    val kind: MiniMaxVoiceKind,
    val supportedLanguages: Set<SpeechLanguage> = emptySet(),
    val description: String = "",
)

data class CustomVoiceFavorite(
    val id: String,
    val displayName: String,
    val voiceId: String,
    val languages: Set<SpeechLanguage>,
)

data class MiniMaxVoiceCatalog(
    val voices: List<MiniMaxVoice>,
    val fetchedAt: Long,
)

object BuiltInMiniMaxVoices {
    val voices: List<MiniMaxVoice> = buildList {
        fun addVoice(id: String, name: String, language: SpeechLanguage) {
            add(MiniMaxVoice(id, name, MiniMaxVoiceKind.SYSTEM, setOf(language)))
        }

        listOf(
            "Santa_Claus" to "Santa Claus",
            "Grinch" to "Grinch",
            "Rudolph" to "Rudolph",
            "Arnold" to "Arnold",
            "Charming_Santa" to "Charming Santa",
            "Serene_Woman" to "Serene Woman",
            "Charming_Lady" to "Charming Lady",
            "Sweet_Girl" to "Sweet Girl",
            "Cute_Elf" to "Cute Elf",
            "Attractive_Girl" to "Attractive Girl",
            "English_Graceful_Lady" to "Graceful Lady",
            "English_Whispering_girl" to "Whispering Girl",
            "English_Trustworthy_Man" to "Trustworthy Man",
            "English_Diligent_Man" to "Diligent Man",
            "English_Gentle-voiced_man" to "Gentle-voiced Man",
            "English_Aussie_Bloke" to "Aussie Bloke",
        ).forEach { (id, name) -> addVoice(id, name, SpeechLanguage.ENGLISH_US) }

        listOf(
            "Cantonese_ProfessionalHost（F)" to "专业女主持",
            "Cantonese_GentleLady" to "温柔女声",
            "Cantonese_ProfessionalHost（M)" to "专业男主持",
            "Cantonese_PlayfulMan" to "活泼男声",
            "Cantonese_CuteGirl" to "可爱女孩",
            "Cantonese_KindWoman" to "善良女声",
        ).forEach { (id, name) -> addVoice(id, name, SpeechLanguage.CANTONESE_HK) }

        listOf(
            "female-tianmei" to "甜美女性音色",
            "female-shaonv" to "少女音色",
            "female-yujie" to "御姐音色",
            "female-chengshu" to "成熟女性音色",
            "male-qn-qingse" to "青涩青年音色",
            "male-qn-jingying" to "精英青年音色",
            "male-qn-badao" to "霸道青年音色",
            "male-qn-daxuesheng" to "青年大学生音色",
            "male-qn-qingse-jingpin" to "青涩青年音色-beta",
            "male-qn-jingying-jingpin" to "精英青年音色-beta",
            "male-qn-badao-jingpin" to "霸道青年音色-beta",
            "male-qn-daxuesheng-jingpin" to "青年大学生音色-beta",
            "female-shaonv-jingpin" to "少女音色-beta",
            "female-yujie-jingpin" to "御姐音色-beta",
            "female-chengshu-jingpin" to "成熟女性音色-beta",
            "female-tianmei-jingpin" to "甜美女性音色-beta",
            "clever_boy" to "聪明男童",
            "cute_boy" to "可爱男童",
            "lovely_girl" to "萌萌女童",
            "cartoon_pig" to "卡通猪小琪",
            "bingjiao_didi" to "病娇弟弟",
            "junlang_nanyou" to "俊朗男友",
            "chunzhen_xuedi" to "纯真学弟",
            "lengdan_xiongzhang" to "冷淡学长",
            "badao_shaoye" to "霸道少爷",
            "tianxin_xiaoling" to "甜心小玲",
            "qiaopi_mengmei" to "俏皮萌妹",
            "wumei_yujie" to "妩媚御姐",
            "diadia_xuemei" to "嗲嗲学妹",
            "danya_xuejie" to "淡雅学姐",
            "Chinese (Mandarin)_Reliable_Executive" to "沉稳高管",
            "Chinese (Mandarin)_News_Anchor" to "新闻女声",
            "Chinese (Mandarin)_Unrestrained_Young_Man" to "不羁青年",
            "Arrogant_Miss" to "嚣张小姐",
            "Robot_Armor" to "机械战甲",
            "Chinese (Mandarin)_Kind-hearted_Antie" to "热心大婶",
            "Chinese (Mandarin)_HK_Flight_Attendant" to "港普空姐",
            "Chinese (Mandarin)_Humorous_Elder" to "搞笑大爷",
            "Chinese (Mandarin)_Gentleman" to "温润男声",
            "Chinese (Mandarin)_Warm_Bestie" to "温暖闺蜜",
            "Chinese (Mandarin)_Male_Announcer" to "播报男声",
            "Chinese (Mandarin)_Sweet_Lady" to "甜美女声",
            "Chinese (Mandarin)_Southern_Young_Man" to "南方小哥",
            "Chinese (Mandarin)_Wise_Women" to "阅历姐姐",
            "Chinese (Mandarin)_Gentle_Youth" to "温润青年",
            "Chinese (Mandarin)_Gentle_Senior" to "温柔学姐",
            "Chinese (Mandarin)_Warm_Girl" to "温暖少女",
            "Chinese (Mandarin)_Kind-hearted_Elder" to "花甲奶奶",
            "Chinese (Mandarin)_Cute_Spirit" to "憨憨萌兽",
            "Chinese (Mandarin)_Radio_Host" to "电台男主播",
            "Chinese (Mandarin)_Lyrical_Voice" to "抒情男声",
            "Chinese (Mandarin)_Straightforward_Boy" to "率真弟弟",
            "Chinese (Mandarin)_Sincere_Adult" to "真诚青年",
            "Chinese (Mandarin)_Stubborn_Friend" to "嘴硬竹马",
            "Chinese (Mandarin)_Crisp_Girl" to "清脆少女",
            "Chinese (Mandarin)_Pure-hearted_Boy" to "清澈邻家弟弟",
            "Chinese (Mandarin)_Soft_Girl" to "柔和少女",
            "Chinese (Mandarin)_Mature_Woman" to "成熟女声",
        ).forEach { (id, name) -> addVoice(id, name, SpeechLanguage.MANDARIN_CN) }
    }

    private val byId = voices.associateBy { it.id }

    fun languagesFor(voiceId: String): Set<SpeechLanguage> = byId[voiceId]?.supportedLanguages.orEmpty()

    fun nameFor(voiceId: String): String? = byId[voiceId]?.name

    fun find(voiceId: String): MiniMaxVoice? = byId[voiceId]
}

/** The online voice endpoint does not provide trustworthy locale/accent metadata. */
object MiniMaxVoiceSelectionPolicy {
    private val excludedEnglishAccentTerms = setOf(
        "aussie", "australia", "australian", "india", "indian", "ireland", "irish",
        "south africa", "south_africa", "south-africa", "south african", "south_african",
        "canada", "canadian", "new zealand", "new_zealand", "new-zealand", "kiwi",
    )
    private val explicitAllowedEnglishAccentTerms = setOf(
        "american", "en-us", "en_us", "us english", "english us",
        "british", "en-gb", "en_gb", "uk english", "english uk", "england",
        "northern irish", "northern_irish", "northern-irish",
    )

    fun selectableVoices(language: SpeechLanguage, catalog: List<MiniMaxVoice>): List<MiniMaxVoice> = catalog
        .asSequence()
        .filter { isSelectable(language, it) }
        .distinctBy { it.id }
        .sortedWith(compareBy<MiniMaxVoice> { it.name.lowercase() }.thenBy { it.id })
        .toList()

    fun isSelectable(language: SpeechLanguage, voice: MiniMaxVoice): Boolean {
        if (voice.kind != MiniMaxVoiceKind.SYSTEM) return false
        val official = BuiltInMiniMaxVoices.find(voice.id)
        if (official != null) {
            if (language !in official.supportedLanguages) return false
            return language != SpeechLanguage.ENGLISH_US || !hasExcludedEnglishAccent(voice)
        }
        if (language != SpeechLanguage.ENGLISH_US) return false
        val searchable = voice.searchableAccentText()
        return explicitAllowedEnglishAccentTerms.any(searchable::contains) &&
            !excludedEnglishAccentTerms.any(searchable::contains)
    }

    fun fallbackVoiceId(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "Serene_Woman"
        SpeechLanguage.CANTONESE_HK -> "Cantonese_GentleLady"
        SpeechLanguage.MANDARIN_CN -> "female-tianmei"
    }

    private fun hasExcludedEnglishAccent(voice: MiniMaxVoice): Boolean {
        val searchable = voice.searchableAccentText()
        if (explicitAllowedEnglishAccentTerms.any(searchable::contains) &&
            (searchable.contains("northern irish") || searchable.contains("northern_irish") ||
                searchable.contains("northern-irish"))) {
            return false
        }
        return excludedEnglishAccentTerms.any(searchable::contains)
    }

    private fun MiniMaxVoice.searchableAccentText(): String = "$id $name $description".lowercase()
}
