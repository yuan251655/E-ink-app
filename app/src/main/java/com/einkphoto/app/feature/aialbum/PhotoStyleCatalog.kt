package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.R

/** App-owned, fixed style presets. Prompts are never rendered or editable. */
data class PhotoStylePreset(
    val id: String,
    val title: String,
    val prompt: String,
    val coverRes: Int,
)

object PhotoStyleCatalog {
    val presets = listOf(
        PhotoStylePreset("realistic_oil", "写实油画", "realistic oil painting, rich brushwork and natural light", R.drawable.photo_style_01),
        PhotoStylePreset("watercolor", "水彩画", "delicate watercolor painting, translucent colors and paper texture", R.drawable.photo_style_02),
        PhotoStylePreset("anime", "日系动漫插画", "clean Japanese anime illustration, expressive and refined", R.drawable.photo_style_03),
        PhotoStylePreset("childrens_book", "儿童绘本插画", "warm children's picture-book illustration", R.drawable.photo_style_04),
        PhotoStylePreset("minimal_flat", "现代简约扁平插画", "modern minimal flat illustration, clean shapes and soft colors", R.drawable.photo_style_05),
        PhotoStylePreset("modern_ink", "现代国风水墨画", "modern Chinese ink wash painting, elegant ink and negative space", R.drawable.photo_style_06),
        PhotoStylePreset("paper_cut", "现代剪纸艺术", "modern Chinese paper-cut art, layered colored paper", R.drawable.photo_style_07),
        PhotoStylePreset("woodcut", "彩色木刻版画", "color woodcut print, carved texture and bold layered inks", R.drawable.photo_style_08),
        PhotoStylePreset("clay", "立体黏土艺术", "three-dimensional clay art, handcrafted soft sculpted forms", R.drawable.photo_style_09),
        PhotoStylePreset("pencil", "铅笔素描", "detailed pencil sketch, graphite lines and subtle shading", R.drawable.photo_style_10),
        PhotoStylePreset("fresh_photo", "日系清新摄影", "fresh Japanese lifestyle photography, airy natural light", R.drawable.photo_style_11),
        PhotoStylePreset("line_art", "现代极简线描插画", "modern minimal line-art illustration, refined simple strokes", R.drawable.photo_style_12),
        PhotoStylePreset("crayon", "蜡笔画", "childlike crayon drawing, tactile wax texture", R.drawable.photo_style_13),
        PhotoStylePreset("american_comic", "美式漫画插画", "American comic illustration, bold outlines and lively colors", R.drawable.photo_style_14),
        PhotoStylePreset("plush", "毛绒玩具", "adorable plush toy style, soft fabric and stitched details", R.drawable.photo_style_15),
        PhotoStylePreset("felt", "毛毡布艺", "warm felt craft style, soft handmade textile texture", R.drawable.photo_style_16),
        PhotoStylePreset("ceramic", "陶瓷玩偶", "glossy ceramic figurine, delicate handcrafted finish", R.drawable.photo_style_17),
        PhotoStylePreset("miniature", "迷你场景模型", "detailed miniature diorama, tiny scale model scene", R.drawable.photo_style_18),
        PhotoStylePreset("building_blocks", "彩色积木玩具", "colorful building-block toy style, playful modular forms", R.drawable.photo_style_19),
        PhotoStylePreset("figure", "三维公仔", "cute three-dimensional character figurine, polished collectible toy", R.drawable.photo_style_20),
        PhotoStylePreset("sticker", "贴纸拼贴", "colorful sticker collage, layered cutout composition", R.drawable.photo_style_21),
        PhotoStylePreset("chibi", "Q版人物插画", "cute chibi character illustration, rounded proportions", R.drawable.photo_style_22),
    )
}
