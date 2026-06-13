param(
    [string] $Prefix = "http://localhost:19090/test/push-notifications/"
)

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add($Prefix)
$listener.Start()

Write-Host "Listening for A2A push notifications at $Prefix"
Write-Host "Press Ctrl+C to stop."

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $reader = [System.IO.StreamReader]::new($request.InputStream, $request.ContentEncoding)
        $body = $reader.ReadToEnd()
        $reader.Dispose()

        Write-Host ""
        Write-Host "===== A2A Push Notification $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ====="
        Write-Host "$($request.HttpMethod) $($request.RawUrl)"
        Write-Host $body

        $responseBody = [System.Text.Encoding]::UTF8.GetBytes("accepted")
        $response = $context.Response
        $response.StatusCode = 202
        $response.ContentType = "text/plain; charset=utf-8"
        $response.OutputStream.Write($responseBody, 0, $responseBody.Length)
        $response.Close()
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
