param([string]$Before='docs/screenshots/fidelity-before',[string]$After='docs/screenshots/fidelity-after/tally-exact-reference',[string]$Output='docs/screenshots/fidelity-comparisons')
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $Output | Out-Null
$names=@('add-money','gpay-schedule','money-planner','export','shared-entry','shared-payments','shared-review','accounts','app-lock','backup','budgets','calendar','categories','category-editor','default-split','diary','favourites','foreign-bill','friends','goals','groups','group-editor','income-entry','merchant-rules','merchant-rule-editor','monthly-review','recurring','reminders','settings','setup-filled','tools','transfer','balance-check')
$font=New-Object System.Drawing.Font 'Segoe UI',14
foreach($name in $names) {
    $oldPath=Join-Path (Get-Location) "$Before/$name.png"; $newPath=Join-Path (Get-Location) "$After/$name.png"
    if(!(Test-Path -LiteralPath $oldPath) -or !(Test-Path -LiteralPath $newPath)){continue}
    $old=[System.Drawing.Image]::FromFile($oldPath);$new=[System.Drawing.Image]::FromFile($newPath)
    $oldHeight=[int]($old.Height*393.0/$old.Width);$newHeight=[int]($new.Height*393.0/$new.Width)
    $canvas=New-Object System.Drawing.Bitmap 806,([Math]::Max($oldHeight,$newHeight)+40)
    $graphics=[System.Drawing.Graphics]::FromImage($canvas);$graphics.Clear([System.Drawing.Color]::White)
    $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawString("Before · $name · 393dp",$font,[System.Drawing.Brushes]::Black,4,5)
    $graphics.DrawString("After · $name · 393dp",$font,[System.Drawing.Brushes]::Black,407,5)
    $graphics.DrawImage($old,0,40,393,$oldHeight);$graphics.DrawImage($new,413,40,393,$newHeight)
    $canvas.Save((Join-Path (Get-Location) "$Output/$name.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose();$canvas.Dispose();$old.Dispose();$new.Dispose()
}
$font.Dispose()
