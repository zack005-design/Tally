$uiPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/ui/HomeScreen.kt'
$uiText=[IO.File]::ReadAllText($uiPath).Replace('"groups" to "Groups",','"groups" to "Groups","default-splits" to "Default group splits","recurring" to "Recurring entries","goals" to "Savings goals",')
[IO.File]::WriteAllText($uiPath,$uiText)
$navPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/ui/TrackerApp.kt'
$navText=[IO.File]::ReadAllText($navPath).Replace('ui.snapshot.groups,model::change,model::parseText','ui.snapshot.groups,{value->val group=ui.snapshot.groups.find {it.id==value.groupId && value.groupId!=form.groupId};model.change(if(group==null) value else model.withGroupDefault(value,group))},model::parseText')
[IO.File]::WriteAllText($navPath,$navText)
$testPath=Join-Path (Get-Location) 'app/src/androidTest/java/com/smartexpense/PersonalWalletRepositoryTest.kt'
$testText=[IO.File]::ReadAllText($testPath).Replace('AppModule.migration1To2,AppModule.migration2To3)','AppModule.migration1To2,AppModule.migration2To3,AppModule.migration3To4)')
[IO.File]::WriteAllText($testPath,$testText)
