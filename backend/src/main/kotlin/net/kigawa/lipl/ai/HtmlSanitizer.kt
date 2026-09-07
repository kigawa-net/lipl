package net.kigawa.lipl.ai

import org.owasp.html.CssSchema
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.Sanitizers

// OWASPのデフォルトCSSスキーマ（Sanitizers.STYLES）はobject-fit/display/overflowを
// 許可しないため、widthとheightを固定指定した<img>がそのままstyle属性から
// object-fitだけ剥ぎ取られ、画像が引き伸ばされて潰れて見える不具合があった。
// キーワードのみを取る安全なプロパティとして追加で許可する。
private val EXTRA_CSS_SCHEMA = CssSchema.withProperties(
    mapOf(
        "object-fit" to CssSchema.Property(0, setOf("fill", "contain", "cover", "none", "scale-down"), emptyMap()),
        "display" to CssSchema.Property(
            0,
            setOf("none", "block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid"),
            emptyMap(),
        ),
        "overflow" to CssSchema.Property(0, setOf("visible", "hidden", "clip", "scroll", "auto"), emptyMap()),
        "overflow-x" to CssSchema.Property(0, setOf("visible", "hidden", "clip", "scroll", "auto"), emptyMap()),
        "overflow-y" to CssSchema.Property(0, setOf("visible", "hidden", "clip", "scroll", "auto"), emptyMap()),
    ),
)

// AIが生成した/オーナーが手動編集したページHTMLは、そのまま公開LPに埋め込んで
// 表示するため（dangerouslySetInnerHTML相当）、script・イベントハンドラ・
// 外部リソース読み込み等を厳格に除去する。OWASP Java HTML Sanitizerの
// 定評あるビルトインポリシーを組み合わせ、独自要素はclass属性のみ追加で許可する。
private val POLICY = Sanitizers.FORMATTING
    .and(Sanitizers.BLOCKS)
    .and(Sanitizers.LINKS)
    .and(Sanitizers.IMAGES)
    .and(
        HtmlPolicyBuilder()
            .allowStyling(CssSchema.union(CssSchema.DEFAULT, EXTRA_CSS_SCHEMA))
            .toFactory(),
    )
    .and(Sanitizers.TABLES)
    .and(
        HtmlPolicyBuilder()
            .allowElements("div", "section", "header", "footer", "main", "article", "span", "nav")
            .allowAttributes("class").globally()
            .toFactory(),
    )

fun sanitizeGeneratedHtml(rawHtml: String): String = POLICY.sanitize(rawHtml)
