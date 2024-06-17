package com.apollographql.execution

fun sandboxHtml(title: String, initialEndpoint: String): String {
  return """
    <!DOCTYPE html>
    <html lang="en" style="display:table;width:100%;height:100%;">

    <head>
        <meta charset="utf-8">
        <title>$title</title>
    </head>

    <body style="height: 100%; display:table-cell;">
    <div style="width: 100%; height: 100%;" id='embedded-sandbox'></div>
    <script src="https://embeddable-sandbox.cdn.apollographql.com/_latest/embeddable-sandbox.umd.production.min.js"></script>
    <script>
      new window.EmbeddedSandbox({
        target: '#embedded-sandbox',
        initialEndpoint: '$initialEndpoint',
      });
    </script>
    </body>

    </html>
""".trimIndent()
}
