package com.einkphoto.app.ui.aialbum

import android.content.Context

/**
 * App-bundled creative prompts. The source stays as Markdown so it remains
 * easy to curate by hand; parsing occurs locally and never reaches the model
 * until the user edits and explicitly requests generation.
 */
internal enum class AiPromptTemplateCategory(val title: String, private val heading: String) {
    Landscape("风景", "风景模板"),
    People("人物", "人物模板"),
    Animals("动物", "动物模板"),
    Artwork("绘画作品", "绘画作品模板"),
    Daily("治愈日常", "治愈日常模板"),
    Fantasy("幻想梦境", "幻想梦境模板"),
    Anime("动漫人物", "动漫人物模板"),
    Couple("情侣与纪念", "情侣与纪念模板");

    companion object {
        fun fromHeading(value: String): AiPromptTemplateCategory? = entries.firstOrNull { it.heading == value }
    }
}

internal data class AiPromptTemplate(
    val id: String,
    val category: AiPromptTemplateCategory,
    val text: String,
)

internal object AiPromptTemplateCatalog {
    private const val assetName = "ai_prompt_templates.md"
    private const val templatesPerCategory = 80

    fun load(context: Context): List<AiPromptTemplate> = runCatching {
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
            val items = mutableListOf<AiPromptTemplate>()
            var category: AiPromptTemplateCategory? = null
            var id: String? = null
            val lines = mutableListOf<String>()

            fun flush() {
                val activeCategory = category
                val activeId = id
                if (activeCategory != null && !activeId.isNullOrBlank() && lines.isNotEmpty()) {
                    items += AiPromptTemplate(activeId, activeCategory, lines.joinToString("\n"))
                }
                id = null
                lines.clear()
            }

            reader.forEachLine { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("## ") -> {
                        flush()
                        category = AiPromptTemplateCategory.fromHeading(line.removePrefix("## ").trim())
                    }
                    line.startsWith("### ") -> {
                        flush()
                        id = line.removePrefix("### ").trim()
                    }
                    id != null && line.isNotBlank() -> lines += line
                }
            }
            flush()
            completeCategories(items)
        }
    }.getOrDefault(emptyList())

    private fun completeCategories(loaded: List<AiPromptTemplate>): List<AiPromptTemplate> =
        AiPromptTemplateCategory.entries.flatMap { category ->
            val existing = loaded.filter { it.category == category }
                .mapIndexed { index, template ->
                    template.copy(text = enrichPrompt(category, template.text, index))
                }
            existing + generatedTemplates(category, existing.size, templatesPerCategory - existing.size)
        }

    private fun generatedTemplates(
        category: AiPromptTemplateCategory,
        startIndex: Int,
        count: Int,
    ): List<AiPromptTemplate> {
        val promptSet = promptSets.getValue(category)
        return (startIndex until (startIndex + count)).map { index ->
            val subject = promptSet.subjects[index / promptSet.scenes.size]
            val scene = promptSet.scenes[index % promptSet.scenes.size]
            val style = promptSet.styles[index % promptSet.styles.size]
            AiPromptTemplate(
                id = "${promptSet.prefix}-${(index + 1).toString().padStart(3, '0')}",
                category = category,
                text = enrichPrompt(
                    category = category,
                    base = "创作一幅以$subject 为主体的画面。场景要求：$scene。视觉风格：$style。",
                    index = index,
                ),
            )
        }
    }

    private fun enrichPrompt(
        category: AiPromptTemplateCategory,
        base: String,
        index: Int,
    ): String = buildString {
        append(base.trim().trimEnd('。', '，', ',', ';', '；'))
        append("。画幅为横向 5:3，采用${compositions[index % compositions.size]}，")
        append("主体位于${subjectPositions[index % subjectPositions.size]}，轮廓完整清晰，不被边缘裁切；")
        append("前景、中景和远景层次分明，并利用${depthDetails[index % depthDetails.size]}增强空间纵深。")
        append(categoryDetails.getValue(category))
        append("光线采用${lighting[index % lighting.size]}，主光方向明确，阴影柔和且保留细节；")
        append("整体配色以${palettes[index % palettes.size]}为主，控制色彩数量和饱和度，形成清晰的冷暖关系。")
        append("材质、衣物、毛发、植物、建筑和环境纹理应符合真实结构，边缘干净，细节丰富但不堆叠。")
        append("最终画面需适合 800×480 六色电子墨水屏显示：强化主体与背景的明度区分，避免大面积灰雾、过暗阴影、细碎噪点和低对比元素。")
        append(textInstruction(category, index))
        append("不要水印、品牌标志、界面元素、边框、随机字母、乱码或与主题无关的文字。")
    }

    private fun textInstruction(category: AiPromptTemplateCategory, index: Int): String = when {
        category == AiPromptTemplateCategory.Couple && index in 10..19 ->
            "蛋糕或背景卡片上可以自然出现且只出现中文短句“生日快乐”，字形清楚、笔画正确，不遮挡人物。"
        category == AiPromptTemplateCategory.Couple && index in 30..39 ->
            "可以在旅行明信片上自然加入且只加入中文短句“一起去更远的地方”，文字清晰、小而克制。"
        category == AiPromptTemplateCategory.Couple && index in 40..49 ->
            "信纸上可以自然出现且只出现中文短句“想你了”，文字清晰，其他正文保持不可见。"
        category == AiPromptTemplateCategory.Couple && index in 70..79 ->
            "礼物卡片上可以自然出现且只出现中文短句“纪念日快乐”，排版简洁，文字不能变形。"
        category == AiPromptTemplateCategory.Daily && index in 70..79 ->
            "面包店招牌可以自然出现且只出现中文短句“今日新鲜出炉”，字形端正、清晰可读。"
        category == AiPromptTemplateCategory.Artwork && index % 20 == 0 ->
            "画面角落可以加入一枚小巧的中式印章，印文只写“平安喜乐”，不能出现其他文字。"
        else -> "画面中不出现任何文字、数字、字母或符号。"
    }

    private val compositions = listOf(
        "三分法构图", "中央稳定构图", "对角线引导构图", "低机位广角构图",
        "平视中景构图", "大面积留白构图", "框景式构图", "视觉动线由近及远的构图",
    )
    private val subjectPositions = listOf("画面左侧三分之一", "画面右侧三分之一", "视觉中心略偏下", "视觉中心略偏上")
    private val depthDetails = listOf("前景遮挡与远景空气透视", "道路、河流或光影形成的引导线", "大小比例变化与轻微景深", "疏密变化和清晰度递减")
    private val lighting = listOf("清晨柔和侧光", "傍晚金色逆光", "阴天均匀漫射光", "窗边自然光", "月光与暖色环境光", "穿过云层或树叶的体积光", "电影感轮廓光", "柔和顶光与局部反射光")
    private val palettes = listOf("奶油白、浅粉与暖棕", "天空蓝、云白与少量暖黄", "墨绿、米白与木质棕", "淡紫、月白与深蓝", "樱花粉、象牙白与灰蓝", "橙红、金黄与沉稳深蓝", "青绿、浅灰与柔和米色", "砖红、墨黑与复古米黄")
    private val categoryDetails = mapOf(
        AiPromptTemplateCategory.Landscape to "突出自然尺度、天气变化和地貌特征，植被分布合理，天空与地面比例舒展；",
        AiPromptTemplateCategory.People to "人物面部自然，眼神有明确落点，手指数量和关节正确，动作符合重心，服装褶皱跟随姿态；",
        AiPromptTemplateCategory.Animals to "动物品种与体态准确，眼睛有神，四肢、耳朵和尾巴结构正确，毛发方向顺应身体轮廓；",
        AiPromptTemplateCategory.Artwork to "保留所选媒介真实的笔触、颜料厚度、纸张或画布肌理，画面具有完整艺术作品感；",
        AiPromptTemplateCategory.Daily to "加入少量有温度的生活物件，摆放自然有使用痕迹，空间整洁但不过度样板化；",
        AiPromptTemplateCategory.Fantasy to "奇幻元素需要有统一世界观和尺度关系，发光物体照亮周围环境，建筑与生物结构完整可信；",
        AiPromptTemplateCategory.Anime to "严格保留已知角色的标志性发型、五官、服装、配色和道具，不更换身份，不混入其他角色特征；人物手部和肢体结构正确，表情符合原作性格；",
        AiPromptTemplateCategory.Couple to "两人的视线、手势和身体距离自然，互动真实克制，面部与手部结构准确，情感通过动作和环境细节表达；",
    )

    private data class PromptSet(
        val prefix: String,
        val subjects: List<String>,
        val scenes: List<String>,
        val styles: List<String>,
    )

    private val promptSets = mapOf(
        AiPromptTemplateCategory.Landscape to PromptSet(
            prefix = "LAND",
            subjects = listOf("清晨山谷", "海边灯塔", "雨后古镇", "夏日花田", "雪山湖泊", "秋日森林", "云海高原", "星空营地"),
            scenes = listOf("薄雾、溪流与远山相映", "柔和阳光穿过云层", "小径延伸至画面深处", "前景有自然花草与石头", "天空保留开阔的呼吸感", "水面映出安静倒影", "远处点缀一间小木屋", "画面有轻微微风的动态", "暖色夕阳铺在地面上", "整体环境干净而宁静"),
            styles = listOf("细腻风光摄影风格", "清新水彩插画风格", "温柔电影感数字绘画", "东方写意山水风格", "高细节油画风格", "极简旅行海报风格", "梦幻自然纪录片风格", "柔和童话绘本风格"),
        ),
        AiPromptTemplateCategory.People to PromptSet(
            prefix = "PERSON",
            subjects = listOf("窗边阅读的年轻女孩", "雨巷中撑伞的旅人", "海边散步的摄影师", "花园里浇花的人", "咖啡馆写信的女孩", "山顶远望的背包客", "工作室作画的青年", "街头演奏小提琴的人"),
            scenes = listOf("柔和侧光勾勒轮廓", "身后有安静的生活场景", "人物与环境保持自然比例", "画面留出开阔背景", "动作轻松而真实", "服装色彩简洁耐看", "视线带有温柔故事感", "前景有轻微景深虚化", "环境光线温暖柔和", "构图舒展且不拥挤"),
            styles = listOf("电影感人像摄影风格", "日系清新插画风格", "温暖胶片摄影风格", "细腻水彩人物画风格", "柔焦油画肖像风格", "现代杂志封面风格", "治愈系绘本风格", "自然纪实摄影风格"),
        ),
        AiPromptTemplateCategory.Animals to PromptSet(
            prefix = "ANIMAL",
            subjects = listOf("草地上奔跑的小狗", "窗边打盹的橘猫", "花丛中的白色小兔", "湖面游过的天鹅", "森林树枝上的小松鼠", "雪地里回头的小狐狸", "海边追逐浪花的柯基", "夜色中发光的萤火虫群"),
            scenes = listOf("阳光从侧面轻轻照进来", "周围点缀自然植物", "主体表情灵动可爱", "背景干净且有柔和景深", "画面留出安静空间", "环境带有轻微微风感", "前景有细小花草作为层次", "色彩明快但不过度饱和", "光影温暖而柔和", "整体氛围轻松治愈"),
            styles = listOf("自然动物摄影风格", "柔软绘本插画风格", "高细节数字绘画风格", "温暖胶片摄影风格", "童话水彩风格", "轻盈动漫插画风格", "细腻油画风格", "治愈系壁纸风格"),
        ),
        AiPromptTemplateCategory.Artwork to PromptSet(
            prefix = "ART",
            subjects = listOf("一束白色花朵与玻璃花瓶", "月光下的荷塘", "窗边的茶具与书本", "雨夜街角的咖啡店", "向日葵与乡间小路", "安静的海边灯塔", "山间寺庙与松树", "复古唱片机与台灯"),
            scenes = listOf("画面强调留白与层次", "光线形成温柔明暗关系", "材质细节细腻而克制", "主体置于舒适视觉中心", "背景简洁并保留呼吸感", "构图适合横向展示", "画面有安静的叙事感", "色彩层次柔和统一", "环境细节不过度堆叠", "整体具有可长期观看的耐看感"),
            styles = listOf("宋韵国画风格", "印象派油画风格", "柔和水彩插画风格", "现代静物摄影风格", "东方工笔画风格", "极简海报艺术风格", "复古胶片艺术风格", "温暖绘本艺术风格"),
        ),
        AiPromptTemplateCategory.Daily to PromptSet(
            prefix = "DAILY",
            subjects = listOf("阳光窗边蜷着的小猫", "摆着鲜花的早餐餐桌", "雨天的咖啡与书本", "暖灯下的卧室一角", "书桌上的手账和耳机", "奶茶旁的小狗玩偶", "午后阳台上的藤椅", "夜晚街角的小面包店"),
            scenes = listOf("画面有柔和自然光", "桌面整洁并带少量生活小物", "空间安静舒适", "光影有温柔的层次", "背景轻微虚化", "留出适合相框的呼吸感", "色彩轻盈不过度浓烈", "细节带来刚刚好的生活感", "环境干净温暖", "整体像一段被收藏的日常"),
            styles = listOf("日系治愈摄影风格", "柔和绘本插画风格", "温暖胶片风格", "清新水彩风格", "奶油质感数字绘画", "轻复古生活杂志风格", "自然光静物摄影风格", "极简治愈壁纸风格"),
        ),
        AiPromptTemplateCategory.Fantasy to PromptSet(
            prefix = "FANTASY",
            subjects = listOf("漂浮在云海上的小岛", "月光里的花园城堡", "森林深处的精灵小屋", "星空下的鲸鱼列车", "会发光的蘑菇山谷", "漂在宇宙中的花园", "被巨树守护的村庄", "通向天空的古老阶梯"),
            scenes = listOf("天空有柔和的星光", "远处云层缓慢流动", "主体周围有微小发光粒子", "环境层次清晰而不杂乱", "光线梦幻却保持温柔", "画面保留开阔留白", "远景隐藏细小惊喜", "色彩像睡前的温柔想象", "构图具有安静的纵深感", "整体带来轻盈的奇幻感"),
            styles = listOf("梦幻数字插画风格", "细腻童话绘本风格", "柔光奇幻概念艺术风格", "日系幻想动画风格", "水彩幻想风格", "空灵油画风格", "温柔赛璐璐插画风格", "高细节奇幻壁纸风格"),
        ),
        AiPromptTemplateCategory.Anime to PromptSet(
            prefix = "ANIME",
            subjects = listOf(
                "《哆啦A梦》中的哆啦A梦", "《蜡笔小新》中的野原新之助", "《名侦探柯南》中的江户川柯南", "《火影忍者》中的漩涡鸣人", "《海贼王》中的蒙奇·D·路飞",
                "《鬼灭之刃》中的灶门炭治郎", "《美少女战士》中的月野兔", "《龙珠》中的孙悟空", "《精灵宝可梦》中的皮卡丘", "《龙猫》中的龙猫",
                "《哪吒之魔童降世》中的哪吒", "《哪吒之魔童降世》中的敖丙", "《罗小黑战记》中的罗小黑", "《西游记之大圣归来》中的孙悟空", "《刺客伍六七》中的伍六七",
                "《一人之下》中的冯宝宝", "《一人之下》中的张楚岚", "《狐妖小红娘》中的涂山苏苏", "《狐妖小红娘》中的白月初", "《非人哉》中的九月",
            ),
            scenes = listOf(
                "保留角色标志性外形与服装，置于作品代表性的经典场景中",
                "保留角色核心辨识特征，呈现轻松温暖的日常互动瞬间",
                "保留角色原有设定，展现富有动感和故事张力的标志性动作",
                "保留角色经典形象，在柔和光影中呈现适合收藏的纪念画面",
            ),
            styles = listOf("高质量动漫官方海报质感", "细腻赛璐璐上色风格", "经典动画原作氛围", "清新动画剧场版质感", "高细节角色插画风格", "治愈系动漫壁纸风格", "国漫电影概念海报风格", "精致动漫收藏卡插画风格"),
        ),
        AiPromptTemplateCategory.Couple to PromptSet(
            prefix = "LOVE",
            subjects = listOf("傍晚牵手散步的一对情侣", "生日蛋糕前相视而笑的恋人", "异地视频通话后的窗边女孩", "旅行途中并肩看海的情侣", "写着心意卡片的恋人", "雨天共撑一把伞的两个人", "夜晚放烟花时依偎的情侣", "桌上放着礼物与一封信的纪念场景"),
            scenes = listOf("光线温暖而不过分煽情", "画面带有安静陪伴感", "人物互动自然克制", "背景留出舒适空间", "细节体现被珍惜的日常", "构图适合横向相框展示", "环境有轻柔的仪式感", "画面不出现文字或日期", "情绪真诚而温柔", "整体像一段值得收藏的回忆"),
            styles = listOf("温暖电影感摄影风格", "日系恋爱绘本风格", "柔和胶片摄影风格", "清新水彩插画风格", "细腻动漫情侣插画风格", "复古旅行明信片风格", "柔焦油画风格", "治愈系纪念壁纸风格"),
        ),
    )

    fun choose(
        templates: List<AiPromptTemplate>,
        category: AiPromptTemplateCategory,
        previousId: String?,
    ): AiPromptTemplate? {
        val candidates = templates.filter { it.category == category }
        val alternatives = candidates.filterNot { it.id == previousId }
        return (alternatives.ifEmpty { candidates }).randomOrNull()
    }
}
