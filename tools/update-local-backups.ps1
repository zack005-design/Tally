$backupPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/data/BackupService.kt'
$backupText=[IO.File]::ReadAllText($backupPath)
$backupText=$backupText.Replace('put("version",2);put("currency","INR")','put("version",3);put("currency","INR")')
$backupText=$backupText.Replace('listOf("walletSources","allocations"','listOf("resetTargets","gpaySchedule","diary","localSettings","walletSources","allocations"')
$backupText=$backupText.Replace('json.getInt("version") in 1..2','json.getInt("version") in 1..3')
$backupText=$backupText.Replace('if(json.getInt("version")==2) rows("walletSources")','if(json.getInt("version")>=2) rows("walletSources")')
$backupText=$backupText.Replace('else emptyList())','else emptyList(),'+@'
            if(json.getInt("version")>=3) rows("gpaySchedule") {GpayScheduleEntity(it.n(0).toInt(),it.b(1),it.n(2),it.n(3).toInt(),it.s(4),it.s(5))} else emptyList(),
            if(json.getInt("version")>=3) rows("resetTargets") {ResetTargetEntity(it.s(0),it.n(1))} else emptyList(),
            if(json.getInt("version")>=3) rows("diary") {DiaryEntity(it.s(0),it.s(1))} else emptyList(),
            if(json.getInt("version")>=3) rows("localSettings") {LocalSettingsEntity(it.n(0).toInt(),it.s(1),it.s(2),it.b(3),it.s(4))} else emptyList())
'@)
$backupText=$backupText.Replace('val walletSources:List<WalletSourcesEntity>)','val walletSources:List<WalletSourcesEntity>,val schedules:List<GpayScheduleEntity>,val resetTargets:List<ResetTargetEntity>,val diary:List<DiaryEntity>,val settings:List<LocalSettingsEntity>)')
[IO.File]::WriteAllText($backupPath,$backupText)
