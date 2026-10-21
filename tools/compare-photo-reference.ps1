param([string]$After='docs/screenshots/photo-match/tally-exact-reference',[string]$Output='docs/screenshots/photo-match/reference-comparisons')
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $Output | Out-Null
# Bounds exclude the surrounding presentation board; reference and native preserve their own aspect ratio.
$families=@(
 @{Image='01';X=0;Y=0;W=853;H=1844;Names=@('setup-filled')},
 @{Image='02'; X=20;Y=57;W=288;H=792;Step=302;Names=@('tools','groups','default-split','categories','merchant-rules')},
 @{Image='03'; X=17;Y=15;W=333;H=815;Step=347;Xs=@(17,386,733,1080,1427);Widths=@(352,333,333,333,333);Names=@('settings','app-lock','reminders','backup','gpay-schedule')},
 @{Image='05';X=28;Y=32;W=316;H=787;Xs=@(28,395,736,1072,1410);Widths=@(342,314,312,313,336);Names=@('budgets','money-planner','goals','recurring','favourites')},
 @{Image='06';X=19;Y=34;W=289;H=911;Xs=@(19,341,655,944,1245);Widths=@(310,303,278,290,275);Names=@('activity','calendar','diary','monthly-review','export')},
 @{Image='07';X=22;Y=43;W=332;H=783;Xs=@(22,373,725,1074,1424);Widths=@(332,332,331,332,333);Names=@('receipt-import','receipt-review','friend-detail','repayment','offset')},
 @{Image='10';X=17;Y=38;W=334;H=775;Xs=@(17,375,720,1072,1422);Widths=@(345,333,339,334,336);Names=@('setup-superseded','accounts','add-money','transfer','balance-check')},
 @{Image='09';X=20;Y=28;W=333;H=787;Xs=@(20,390,744,1080,1434);Widths=@(354,334,322,337,331);Names=@('income-entry','described-entry','foreign-bill','transaction-personal-detail','refund')},
 @{Image='08'; X=18;Y=18;W=333;H=819;Step=347;Xs=@(18,386,733,1080,1427);Widths=@(356,332,333,334,333);Names=@('shared-entry','shared-payments','shared-method','shared-items','shared-review')}
)
$font=New-Object Drawing.Font 'Segoe UI',14
foreach($family in $families){
 $source=[Drawing.Image]::FromFile((Join-Path (Get-Location) ('docs/design/exact-references/'+$family.Image+'.png')))
 for($i=0;$i -lt $family.Names.Count;$i++){
  $name=$family.Names[$i];$path=Join-Path (Get-Location) ($After+'/'+$name+'.png');if(!(Test-Path -LiteralPath $path)){continue}
  $native=[Drawing.Image]::FromFile($path)
  $cropWidth=if($family.Widths){$family.Widths[$i]}else{$family.W};$cropX=if($family.Xs){$family.Xs[$i]}else{$family.X+$i*$family.Step};$rh=[int]($family.H*393.0/$cropWidth);$nh=[int]($native.Height*393.0/$native.Width)
  $canvas=New-Object Drawing.Bitmap 806,([Math]::Max($rh,$nh)+40);$g=[Drawing.Graphics]::FromImage($canvas);$g.Clear([Drawing.Color]::White)
  $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.DrawString('Photo reference',$font,[Drawing.Brushes]::Black,5,5);$g.DrawString('Native app',$font,[Drawing.Brushes]::Black,418,5)
  $rect=New-Object Drawing.Rectangle $cropX,$family.Y,$cropWidth,$family.H
  $dest=New-Object Drawing.Rectangle 0,40,393,$rh
  $g.DrawImage($source,$dest,$rect,[Drawing.GraphicsUnit]::Pixel)
  $g.DrawImage($native,413,40,393,$nh)
  $canvas.Save((Join-Path (Get-Location) ($Output+'/'+$name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
  $native.Dispose();$g.Dispose();$canvas.Dispose()
 };$source.Dispose()
};$font.Dispose()
