package net.kigawa.lipl.ai

import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.Sanitizers

// AIが生成した/オーナーが手動編集したページHTMLは、そのまま公開LPに埋め込んで
// 表示するため（dangerouslySetInnerHTML相当）、script・イベントハンドラ・
// 外部リソース読み込み等を厳格に除去する。OWASP Java HTML Sanitizerの
// 定評あるビルトインポリシーを組み合わせ、独自要素はclass属性のみ追加で許可する。
private val POLICY = Sanitizers.FORMATTING
    .and(Sanitizers.BLOCKS)
    .and(Sanitizers.LINKS)
    .and(Sanitizers.IMAGES)
    .and(Sanitizers.STYLES)
    .and(Sanitizers.TABLES)
    .and(
        HtmlPolicyBuilder()
            .allowElements("div", "section", "header", "footer", "main", "article", "span", "nav")
            .allowAttributes("class").globally()
            .toFactory(),
    )

fun sanitizeGeneratedHtml(rawHtml: String): String = POLICY.sanitize(rawHtml)
