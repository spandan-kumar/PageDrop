package app.pagedrop.transfer.server

import app.pagedrop.data.local.database.Book

/**
 * Generates HTML pages optimized for the Kindle e-ink browser.
 *
 * The HTML/CSS is ported from the tested kindle-ui/index.html template,
 * confirmed working on Kindle Paperwhite 1. Key constraints:
 * - No JavaScript, no modern CSS (flexbox, grid, etc.)
 * - Pure black on white for max e-ink contrast
 * - Georgia serif font
 * - Large tap targets for imprecise e-ink touchscreen
 */
object KindlePageGenerator {

    /** Formats the Kindle browser can actually download */
    private val KINDLE_NATIVE_FORMATS = setOf("AZW3", "MOBI", "PRC", "TXT")

    private fun isKindleCompatible(book: Book): Boolean =
        book.format.uppercase() in KINDLE_NATIVE_FORMATS

    /**
     * Generate the full HTML page for the given list of books.
     * Handles empty state, single book, and multi-book views.
     */
    fun generatePage(books: List<Book>): String {
        return buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"en\">\n")
            append("<head>\n")
            append("    <meta charset=\"utf-8\">\n")
            append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            append("    <title>PageDrop</title>\n")
            append("    <style>\n")
            append(CSS)
            append("    </style>\n")
            append("</head>\n")
            append("<body>\n\n")

            // Header
            append(HEADER)

            when {
                books.isEmpty() -> appendEmptyState(this)
                books.size == 1 -> appendSingleBookView(this, books.first())
                else -> appendMultiBookView(this, books)
            }

            // Footer
            append(FOOTER)

            append("\n</body>\n")
            append("</html>\n")
        }
    }

    private fun appendEmptyState(sb: StringBuilder) {
        sb.append("\n    <div class=\"empty-state\">\n")
        sb.append("        <p>No books queued</p>\n")
        sb.append("        <div class=\"hint\">Select a book on your phone to sync</div>\n")
        sb.append("    </div>\n")
    }

    private fun appendSingleBookView(sb: StringBuilder, book: Book) {
        sb.append("\n    <div class=\"status ready\">\n")
        sb.append("        1 book ready to sync\n")
        sb.append("    </div>\n\n")
        appendBookCard(sb, book)
    }

    private fun appendMultiBookView(sb: StringBuilder, books: List<Book>) {
        val syncable = books.count { isKindleCompatible(it) }
        sb.append("\n    <div class=\"status ready\">\n")
        sb.append("        ${syncable} of ${books.size} books ready to sync\n")
        sb.append("    </div>\n\n")
        sb.append("    <div class=\"book-list-header\">Queued Books</div>\n\n")
        for (book in books) {
            appendBookCard(sb, book)
        }
    }

    private fun appendBookCard(sb: StringBuilder, book: Book) {
        val compatible = isKindleCompatible(book)
        val cardClass = if (compatible) "book" else "book book-incompatible"
        sb.append("    <div class=\"$cardClass\">\n")
        sb.append("        <div class=\"book-title\">${escapeHtml(book.title)}</div>\n")
        sb.append("        <div class=\"book-author\">${escapeHtml(book.author)}</div>\n")
        sb.append("        <div class=\"book-meta\">\n")
        sb.append("            <span>${escapeHtml(book.format)}</span>\n")
        sb.append("            <span>&middot;</span>\n")
        sb.append("            <span>${formatFileSize(book.fileSize)}</span>\n")
        sb.append("        </div>\n")
        if (compatible) {
            sb.append("        <a href=\"/download/${book.uid}\" class=\"sync-btn\">&darr; SYNC</a>\n")
        } else {
            val fmt = book.format.uppercase()
            sb.append("        <div class=\"compat-warning\">")
            sb.append("&#9888; Cannot download $fmt via Kindle browser. ")
            sb.append("Convert to MOBI on your phone first.")
            sb.append("</div>\n")
        }
        sb.append("    </div>\n\n")
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    /**
     * Format file size in human-readable form (KB or MB).
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private const val HEADER = """
    <div class="header">
        <h1>PageDrop</h1>
        <div class="tagline">wireless book sync</div>
    </div>
"""

    private const val FOOTER = """
    <div class="footer">
        PageDrop &middot; pagedrop.app
    </div>
"""

    /* === E-INK OPTIMIZED CSS === */
    /* No gradients, no shadows, no animations */
    /* Max contrast: pure black on pure white */
    /* Large tap targets for imprecise e-ink touch */
    private const val CSS = """
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: Georgia, "Times New Roman", serif;
            background: #fff;
            color: #000;
            max-width: 580px;
            margin: 0 auto;
            padding: 20px 16px;
            line-height: 1.4;
            -webkit-text-size-adjust: 100%;
        }

        /* Header */
        .header {
            text-align: center;
            padding-bottom: 16px;
            border-bottom: 2px solid #000;
            margin-bottom: 20px;
        }

        .header h1 {
            font-size: 28px;
            letter-spacing: 1px;
            margin-bottom: 4px;
        }

        .header .tagline {
            font-size: 14px;
            font-style: italic;
            color: #333;
        }

        /* Status Bar */
        .status {
            text-align: center;
            font-size: 15px;
            padding: 10px;
            margin-bottom: 20px;
            border: 1px solid #000;
        }

        .status.ready {
            background: #f0f0f0;
        }

        .status.empty {
            background: #fff;
            color: #666;
            font-style: italic;
        }

        /* Book Card */
        .book {
            border: 2px solid #000;
            padding: 16px;
            margin-bottom: 16px;
        }

        .book-title {
            font-size: 20px;
            font-weight: bold;
            margin-bottom: 4px;
        }

        .book-author {
            font-size: 16px;
            color: #333;
            margin-bottom: 8px;
        }

        .book-meta {
            font-size: 13px;
            color: #555;
            margin-bottom: 14px;
        }

        .book-meta span {
            margin-right: 8px;
        }

        /* Sync Button — Extra large tap target for e-ink touchscreen */
        .sync-btn {
            display: block;
            text-align: center;
            background: #000;
            color: #fff;
            text-decoration: none;
            font-size: 20px;
            font-weight: bold;
            padding: 18px 0;
            border: none;
            letter-spacing: 1px;
        }

        .sync-btn:visited {
            color: #fff;
        }

        .sync-btn:active {
            background: #333;
        }

        /* Sync All Button */
        .sync-all {
            display: block;
            text-align: center;
            background: #000;
            color: #fff;
            text-decoration: none;
            font-size: 22px;
            font-weight: bold;
            padding: 22px 0;
            margin-bottom: 20px;
            border: 3px solid #000;
            letter-spacing: 2px;
        }

        .sync-all:visited {
            color: #fff;
        }

        /* Multiple Books List */
        .book-list-header {
            font-size: 16px;
            font-weight: bold;
            margin-bottom: 12px;
            padding-bottom: 8px;
            border-bottom: 1px solid #999;
        }

        /* Footer */
        .footer {
            text-align: center;
            font-size: 12px;
            color: #666;
            padding-top: 16px;
            border-top: 1px solid #999;
            margin-top: 20px;
        }

        /* Empty State */
        .empty-state {
            text-align: center;
            padding: 40px 16px;
        }

        .empty-state p {
            font-size: 18px;
            color: #555;
            margin-bottom: 10px;
        }

        .empty-state .hint {
            font-size: 14px;
            color: #888;
            font-style: italic;
        }

        /* Success State */
        .success {
            text-align: center;
            padding: 30px 16px;
            border: 2px solid #000;
            margin-bottom: 16px;
        }

        .success .checkmark {
            font-size: 36px;
            margin-bottom: 8px;
        }

        .success p {
            font-size: 18px;
        }

        /* Incompatible format card — grayed out */
        .book-incompatible {
            border-color: #999;
            color: #888;
        }

        .book-incompatible .book-title,
        .book-incompatible .book-author {
            color: #999;
        }

        .compat-warning {
            font-size: 13px;
            color: #666;
            font-style: italic;
            padding: 10px 0 2px 0;
            border-top: 1px dashed #bbb;
            margin-top: 8px;
        }
"""
}
