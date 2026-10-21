$dbPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/data/Database.kt'
$dbText=[IO.File]::ReadAllText($dbPath).Replace('@Database(entities=[GpayScheduleEntity::class','@Database(entities=[PlanEntity::class,GpayScheduleEntity::class').Replace('version=3,exportSchema','version=4,exportSchema')
[IO.File]::WriteAllText($dbPath,$dbText)
$modelPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/domain/Models.kt'
$modelText=[IO.File]::ReadAllText($modelPath).Replace('val localSettings:LocalSettings=LocalSettings())','val localSettings:LocalSettings=LocalSettings(),val groupDefaults:List<GroupDefault> = emptyList(),val templates:List<RecurringTemplate> = emptyList(),val goals:List<SavingsGoal> = emptyList(),val categoryRules:List<CategoryRule> = emptyList())')
[IO.File]::WriteAllText($modelPath,$modelText)
$repoPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/data/FinanceRepository.kt'
$repoText=[IO.File]::ReadAllText($repoPath).Replace('dao.settingsChanges()))','dao.settingsChanges(),dao.planChanges()))').Replace('val members=dao.members();val entries','val plans=dao.plans();val members=dao.members();val entries').Replace('?:LocalSettings())','?:LocalSettings(),groupDefaults=plans.filter {it.kind=="group"}.map(PlanCodec::group),templates=plans.filter {it.kind=="template"}.map(PlanCodec::template),goals=plans.filter {it.kind=="goal"}.map(PlanCodec::goal),categoryRules=plans.filter {it.kind=="category"}.map(PlanCodec::category))')
[IO.File]::WriteAllText($repoPath,$repoText)
$backupPath=Join-Path (Get-Location) 'app/src/main/java/com/smartexpense/data/BackupService.kt'
$backupText=[IO.File]::ReadAllText($backupPath).Replace('put("version",3)','put("version",4)').Replace('in 1..3','in 1..4').Replace('listOf("resetTargets"','listOf("localPlans","resetTargets"').Replace('val settings:List<LocalSettingsEntity>)','val settings:List<LocalSettingsEntity>,val plans:List<PlanEntity>)')
$backupText=$backupText.Replace('it.s(4))} else emptyList())','it.s(4))} else emptyList(),if(json.getInt("version")>=4) rows("localPlans") {PlanEntity(it.s(0),it.s(1),it.s(2))} else emptyList())')
[IO.File]::WriteAllText($backupPath,$backupText)
