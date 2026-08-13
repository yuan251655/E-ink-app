package com.einkphoto.app.ui.aialbum

import android.content.Context

/**
 * App-bundled creative prompts. The source stays as Markdown so it remains
 * easy to curate by hand; parsing occurs locally and never reaches the model
 * until the user edits and explicitly requests generation.
 */
internal enum class AiPromptTemplateCategory(val title: String) {
    Landscape("风景"),
    People("人物"),
    Animals("动物"),
    Artwork("绘画作品"),
    Daily("治愈日常"),
    Fantasy("幻想梦境"),
    Anime("动漫人物"),
    Couple("情侣与纪念"),
}

internal data class AiPromptTemplate(
    val id: String,
    val category: AiPromptTemplateCategory,
    val text: String,
)

internal object AiPromptTemplateCatalog {
    private const val templatesPerCategory = 80

    @Suppress("UNUSED_PARAMETER")
    fun load(context: Context): List<AiPromptTemplate> = buildDistinctCatalog()

    private fun buildDistinctCatalog(): List<AiPromptTemplate> =
        AiPromptTemplateCategory.entries.flatMap { category -> generatedTemplates(category, 0, templatesPerCategory) }
            .also { templates ->
                check(templates.size == AiPromptTemplateCategory.entries.size * templatesPerCategory)
                check(templates.map { it.text }.distinct().size == templates.size)
                AiPromptTemplateCategory.entries.forEach { category ->
                    check(templates.count { it.category == category } == templatesPerCategory)
                }
            }

    private fun generatedTemplates(
        category: AiPromptTemplateCategory,
        startIndex: Int,
        count: Int,
    ): List<AiPromptTemplate> {
        val promptSet = promptSets.getValue(category)
        val moments = categoryMoments.getValue(category)
        return (startIndex until (startIndex + count)).map { index ->
            val subject = promptSet.subjects[index / promptSet.scenes.size]
            val scene = promptSet.scenes[index % promptSet.scenes.size]
            val style = promptSet.styles[(index * 3 + index / promptSet.styles.size) % promptSet.styles.size]
            val moment = moments[index % moments.size]
            val environment = environmentAccents[index / promptSet.scenes.size]
            val treatment = styleTreatments[index / promptSet.styles.size]
            AiPromptTemplate(
                id = "${promptSet.prefix}-${(index + 1).toString().padStart(3, '0')}",
                category = category,
                text = renderPrompt(
                    index = index,
                    subjectMoment = "$subject，$moment",
                    scene = "$scene；$environment",
                    style = "$style，$treatment",
                    detail = categoryDetails.getValue(category),
                    textRule = textInstruction(category, index),
                ),
            )
        }
    }

    private fun renderPrompt(
        index: Int,
        subjectMoment: String,
        scene: String,
        style: String,
        detail: String,
        textRule: String,
    ): String {
        val composition = compositions[index % compositions.size]
        val light = lighting[index % lighting.size]
        val palette = palettes[index % palettes.size]
        val core = when (index % 12) {
            0 -> "$subjectMoment。镜头来到$scene，以${composition}组织画面；采用$style，$light，色彩选择$palette。"
            1 -> "在$scene，记录$subjectMoment。画面运用$style，由${light}塑造层次，并以${palette}完成$composition。"
            2 -> "把${scene}作为故事舞台：$subjectMoment。用${style}呈现，${composition}配合$light，主色关系为$palette。"
            3 -> "$scene。此刻，$subjectMoment；视觉语言取自$style，借${light}和${palette}形成$composition。"
            4 -> "从${composition}展开一段画面叙事，内容是$subjectMoment，环境设在$scene。表现方式为$style，使用${light}与$palette。"
            5 -> "描绘$subjectMoment，让${scene}自然交代前因后果。选择$style，不采用普通棚拍感；以${light}统领$palette。"
            6 -> "横向画面里，${scene}逐层展开，$subjectMoment。以${style}刻画关键细节，构图采用$composition，光色为$light、$palette。"
            7 -> "故事发生于$scene，视觉焦点落在$subjectMoment。整体不是素材拼贴，而以$style、${light}和${palette}形成完整作品。"
            8 -> "先用${scene}建立空间，再表现$subjectMoment。采用${composition}控制视线，以${style}结合$light，色调限定为$palette。"
            9 -> "$subjectMoment；周围的${scene}提供叙事线索。画面追求${style}的质感，使用$composition、${light}和$palette。"
            10 -> "设计一幕关于“$subjectMoment”的场景，地点与细节为$scene。艺术处理选择$style，${light}照亮主体，${palette}维持整体秩序。"
            else -> "让观者先看到$subjectMoment，随后从${scene}读出故事。以${style}完成$composition，光线设为$light，配色采用$palette。"
        }
        return "$core$detail${displayRules[index % displayRules.size]}$textRule"
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
    private val lighting = listOf("清晨柔和侧光", "傍晚金色逆光", "阴天均匀漫射光", "窗边自然光", "月光与暖色环境光", "穿过云层或树叶的体积光", "电影感轮廓光", "柔和顶光与局部反射光")
    private val palettes = listOf("奶油白、浅粉与暖棕", "天空蓝、云白与少量暖黄", "墨绿、米白与木质棕", "淡紫、月白与深蓝", "樱花粉、象牙白与灰蓝", "橙红、金黄与沉稳深蓝", "青绿、浅灰与柔和米色", "砖红、墨黑与复古米黄")
    private val environmentAccents = listOf(
        "前景散落被风吹动的细小物件，远处保留开阔留白",
        "一条弯曲路径连接近景与远景，空气透视随距离递减",
        "局部倒影回应主体，边缘处只留下少量环境线索",
        "高低错落的遮挡关系建立三层空间，背景不过度虚化",
        "天气留下可见痕迹，材质表面同时呈现干湿与新旧差异",
        "远方安排一个很小的叙事伏笔，近处纹理清楚但不喧宾夺主",
        "环境中的风向、植物和衣物褶皱保持一致，空间尺度可信",
        "利用建筑或自然形成框景，画外空间仍让人产生联想",
        "地面留有此前事件的痕迹，背景人物只承担尺度参照",
        "光影越过一件半透明物体，在墙面形成不规则投影",
        "近景设置一处被触碰过的细节，让静态空间带有时间感",
        "主场景之外露出一小段相邻空间，暗示故事仍在继续",
        "天空、地面与主体分别占据不同明度区间，层次一眼可辨",
        "一处颜色呼应从主体延伸至远景，但不形成刻意对称",
        "环境中只有一个方向性动态，其余元素保持安静稳定",
        "以不同尺度的重复形状串起视线，避免装饰性堆砌",
        "背景保留真实使用痕迹，物件朝向符合人物刚才的动作",
        "让一束反射光照亮通常被忽略的角落，形成第二阅读点",
        "边缘区域逐步降低信息密度，把观看注意力送回主体",
        "远近材质使用不同颗粒尺度，增强空间而非依赖模糊",
    )
    private val styleTreatments = listOf(
        "保留克制颗粒与清楚边缘", "强调透明叠色和纸张呼吸感", "用长短笔触区分主体与背景", "以硬边剪影衬托柔和内部细节",
        "加入轻微年代印刷质感但不做旧过度", "让局部高光成为视觉节奏", "采用疏密变化代替繁复装饰", "把材质差异作为主要表现线索",
        "控制景深而不模糊关键叙事物件", "以冷暖小面积互补建立安静张力",
    )
    private val displayRules = listOf(
        "横向 5:3，适配六色电子纸，主体边界和明度层次清楚。",
        "按 800×480 横屏设计，减少灰雾与细碎噪点，保留大色块关系。",
        "画面需适合六色墨水屏长期展示，暗部不糊成一片，亮部不过曝。",
        "保持横向相框比例，重要内容远离边缘，有限色阶下仍能辨认。",
        "为电子纸强化轮廓、冷暖和前后层次，不依赖细微渐变表达主题。",
        "最终呈现简洁耐看，避免低对比背景吞没主体，尺寸比例为 5:3。",
        "控制颜色数量，让六色显示仍保留故事重点和材质区别。",
        "采用适合电子墨水屏的清晰块面，避免过黑阴影和密集高频纹理。",
    )
    private val categoryMoments = mapOf(
        AiPromptTemplateCategory.Landscape to listOf(
            "一阵风刚把雾推离水面", "云隙光正在沿山脊移动", "骤雨停歇后第一束光落下", "潮水漫过礁石留下白色水线",
            "候鸟掠过天空改变了原本的宁静", "远处炊烟升起并被风拉长", "融雪汇成细流穿过石缝", "落叶旋转着停在潮湿路面",
            "夜色降临时地平线仍保留余晖", "薄冰裂开后映出清澈水色",
        ),
        AiPromptTemplateCategory.People to listOf(
            "正俯身修好一件陪伴多年的旧物", "刚收到远方来信，读到一半停下来微笑", "迎着风整理被吹乱的衣领", "把最后一笔颜色落在尚未完成的作品上",
            "弯腰拾起孩子遗落的纸飞机", "在列车到站前回头确认站牌", "捧住一束快被雨打湿的鲜花", "借窗上倒影悄悄整理表情",
            "为陌生旅人指向地图上的岔路", "吹灭桌边蜡烛后静静听雨",
        ),
        AiPromptTemplateCategory.Animals to listOf(
            "忽然发现草叶间晃动的小昆虫", "叼回玩具却故意绕开主人伸出的手", "用鼻尖试探刚落下的第一片雪", "跃过浅水时溅起一串明亮水珠",
            "守在幼崽身边警觉地望向远处", "从睡梦中醒来伸展四肢", "循着气味钻进半开的野餐篮", "在倒影前歪头观察另一个自己",
            "追随一束移动的光跑过地面", "把找到的小果实藏进树根缝隙",
        ),
        AiPromptTemplateCategory.Artwork to listOf(
            "让主形体从大片留白中缓慢显现", "以一条连续线串联彼此分离的物件", "用刮刀露出底层颜色形成时间痕迹", "把倒影处理成与现实不同的第二叙事",
            "让颜料自然流淌并在边缘凝结", "以重复纹样建立安静节拍", "让纸张纤维参与云雾和水面的塑造", "用不完整轮廓邀请观者补全画面",
            "将日常物件重新排列成象征性静物", "通过厚薄不同的笔触表现光线经过",
        ),
        AiPromptTemplateCategory.Daily to listOf(
            "早餐刚吃到一半，热气仍停留在杯口", "洗好的床单被风吹出柔软弧线", "新买的花还没修剪，包装纸散在桌边", "唱片播放到末尾，唱针发出轻微回响",
            "烤箱计时器响起，面包表面刚刚上色", "雨伞靠在门边滴水，鞋印延伸进屋", "午睡醒来，书页被窗风翻到下一章", "灯串亮起后，阳台植物投下细长影子",
            "手账写到一半，胶带和照片尚未收好", "夜归的人把钥匙放下，玄关小灯随即亮起",
        ),
        AiPromptTemplateCategory.Fantasy to listOf(
            "漂浮大陆的锚链突然从云层中升起", "沉睡巨兽呼吸时让整片森林明暗起伏", "星星坠入湖中化成一群透明游鱼", "无人的列车沿月环驶向倒悬城市",
            "花朵在脚步经过后依次点亮", "钟楼敲响时天空短暂出现第二轮太阳", "纸船载着微型村庄穿过银河瀑布", "古老门扉打开后露出四季同时存在的庭院",
            "鲸群从云海跃出并拖曳发光雨幕", "地图上的墨线自行生长成真实道路",
        ),
        AiPromptTemplateCategory.Anime to listOf(
            "完成一次符合原作性格的小小善举", "在熟悉伙伴出现前独自处理突发麻烦", "暂时放下使命，认真体验普通人的一天", "用标志性能力化解一场并不宏大的危机",
            "发现来自另一段剧情时间线的旧物", "在节日摊位前做出令人意外的选择", "经历战斗后安静整理重要道具", "与原作中的象征性环境产生新互动",
            "为了守护身后的人摆出经典姿态", "在黄昏告别时露出符合角色经历的表情",
        ),
        AiPromptTemplateCategory.Couple to listOf(
            "交换刚写好的信，却约定回家后再拆开", "在错过末班车后分享同一副耳机", "一起修复旅行中摔裂的小纪念品", "隔着玻璃为对方比出只有两人懂的手势",
            "把两张不同城市的车票夹进同一本书", "争着替对方挡雨，最后都淋湿了肩膀", "悄悄把生日蜡烛摆成相识年份", "在人群走散后凭熟悉的围巾找到彼此",
            "将共同养大的植物搬到新家窗边", "收拾旧照片时同时指向同一个瞬间",
        ),
    )
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
