package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.smartexpense.domain.*

@Composable fun TrackerApp(model:AppViewModel) {
    val ui by model.state.collectAsStateWithLifecycle();val form by model.entry.collectAsStateWithLifecycle()
    val billExpected by model.billExpected.collectAsStateWithLifecycle()
    val repaymentDraft by model.repaymentDraft.collectAsStateWithLifecycle()
    val reportMonth by model.reportMonth.collectAsStateWithLifecycle()
    val reviewMonth by model.reviewMonth.collectAsStateWithLifecycle()
    val review by model.monthlyReview.collectAsStateWithLifecycle()
    val launchRequest by model.launchRequest.collectAsStateWithLifecycle()

    val nav=rememberNavController();val current by nav.currentBackStackEntryAsState();val route=current?.destination?.route
    val exitGuard=remember {NavigationExitGuard()}
    var replacementFavourite by rememberSaveable {mutableStateOf<String?>(null)}
    val paymentAccounts=walletPaymentAccounts(ui.snapshot)
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(ui.message) {ui.message?.let {snackbar.showSnackbar(it);model.dismissMessage()}}
    LaunchedEffect(repaymentDraft) {if(repaymentDraft!=null) nav.navigate("repayment/all")}
    LaunchedEffect(route) {model.observeRoute(route?:"home")}
    LaunchedEffect(route,ui.snapshot.walletSources) {
        if(route=="balance-setup" && ui.snapshot.walletSources!=null) {
            nav.navigate("home") {popUpTo("balance-setup") {inclusive=true};launchSingleTop=true}
        }
    }
    fun add(kind:Kind=Kind.EXPENSE,shared:Boolean=false) {model.newEntry(kind,shared);nav.navigate("add")}
    fun detail(id:String) {nav.navigate("detail/$id")}
    fun openFavourite(id:String?) {
        val favourite=model.state.value.snapshot.favourites.find {it.id==id}
        if(favourite!=null) {model.favouriteEntry(favourite);nav.navigate("add") {launchSingleTop=true}}
        else nav.navigate("favourites") {launchSingleTop=true}
    }
    fun unfinishedEntry():Boolean {
        val draft=model.entry.value
        return model.billExpected.value || draft.replaces!=null || draft.kind!=Kind.EXPENSE || draft.amount.isNotBlank() ||
            draft.description.isNotBlank() || draft.text.isNotBlank() || draft.notes.isNotBlank() || draft.shared ||
            draft.categoryId!=null || draft.groupId!=null || draft.items.isNotBlank() || draft.refundOf!=null ||
            draft.secondPayment.isNotBlank() || draft.paid.values.any {it.isNotBlank()} || draft.values.values.any {it.isNotBlank()}
    }
    LaunchedEffect(launchRequest,ui.loading,ui.snapshot.walletSources) {
        if(!ui.loading && ui.snapshot.walletSources!=null && launchRequest!=null) {
            val request=launchRequest
            val favouriteId=model.requestedFavouriteId
            model.consumeLaunch()
            exitGuard.navigate {
                when(request) {
                    "quick-add"->add()
                    "favourite"->{
                        if(model.state.value.snapshot.favourites.none {it.id==favouriteId}) nav.navigate("favourites") {launchSingleTop=true}
                        else if(unfinishedEntry()) replacementFavourite=favouriteId
                        else openFavourite(favouriteId)
                    }
                    else->nav.navigate("backup") {launchSingleTop=true}
                }
            }
        }
    }
    Scaffold(snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(route in listOf("home","insights","split") && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)==0) LedgerNavigation(route) {destination->exitGuard.navigate {
            if(destination=="add") add() else nav.navigate(destination) {popUpTo("home") {saveState=true};launchSingleTop=true;restoreState=true}
        }}
    }) {padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {CircularProgressIndicator()} else CompositionLocalProvider(LocalNavigationExitGuard provides exitGuard,LocalEditorialBack provides if(route!=null && route !in listOf("home","insights","add","split","balance-setup")) ({nav.popBackStack();Unit}) else null) {NavHost(nav,startDestination=remember {launchDestination(ui.snapshot)},modifier=Modifier.weight(1f),
                // Compose's animation clock respects Android's animator duration scale, including zero.
                enterTransition = {
                    if(initialState.destination.route in mainTabs && targetState.destination.route in mainTabs) fadeIn(tween(180))
                    else slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(220))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { slideInHorizontally(tween(260)) { -it / 12 } + fadeIn(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(160)) }
            ) {
                composable("balance-setup") {BalanceSetupScreen(ui.snapshot,ui.busy) {wallet,gpay,walletId,gpayId->
                    model.action("Balances saved") {
                        model.repository.configureWallet(wallet,walletId,gpayId,gpayOpening=gpay)
                        nav.navigate("home") {popUpTo("balance-setup") {inclusive=true};launchSingleTop=true}
                    }
                }}
                composable("home") {HomeScreen(ui.snapshot,::add,{nav.navigate("accounts")},{nav.navigate("repayment/all")},::detail,{nav.navigate(it)},onRepeat={id->model.repeatExpense(id) {nav.navigate("add")}})}
                composable("activity") {ActivityScreen(ui.snapshot,::detail,onCalendar={nav.navigate("calendar")},onSettings={nav.navigate("more")})}
                composable("calendar") {SpendingCalendarScreen(ui.snapshot,::detail,onDiary={nav.navigate("diary/$it")})}
                composable("gpay-settings") {GpaySettingsScreen(ui.snapshot,ui.busy,onBalance={amount,finished->model.action("GPay balance updated") {model.repository.reconcile(ui.snapshot.walletSources!!.gpayId,amount);finished()}},onSchedule={enabled,target,day,month,finished->model.action("GPay reset settings saved") {model.repository.saveGpaySchedule(enabled,target,day,month);finished()}},onSetup={nav.navigate("wallet")})}
                fun androidx.navigation.NavGraphBuilder.diaryRoute(path:String) {composable(path) {entry->DiaryScreen(ui.snapshot,ui.busy,
                    onSave={date,text,finished->model.action("Diary saved") {model.repository.saveDiary(date,text);finished()}},
                    onAdd={date,shared->model.newEntry(Kind.EXPENSE,shared);model.change(model.entry.value.copy(date=date.atTime(12,0).toString()));nav.navigate("add")},onDetails=::detail,initialDate=entry.arguments?.getString("date")?:java.time.LocalDate.now().toString())}}
                diaryRoute("diary");diaryRoute("diary/{date}")
                composable("monthly-review") {MonthlyReviewScreen(ui.snapshot,review,reviewMonth,model::selectReviewMonth) {model.acknowledgeReview {nav.popBackStack()}}}
                composable("more") {MoreScreen(onNavigate={when(it) {"income"->add(Kind.INCOME);"transfer"->add(Kind.TRANSFER);else->nav.navigate(it)}})}
                composable("tools/{section}") {entry->
                    if(entry.arguments?.getString("section")=="settings") SettingsOverviewScreen(ui.snapshot.localSettings) {nav.navigate(it)}
                    else MoreScreen(entry.arguments?.getString("section")) {when(it) {"income"->add(Kind.INCOME);"transfer"->add(Kind.TRANSFER);else->nav.navigate(it)}}
                }
                composable("profile") {ProfileScreen(ui.snapshot.localSettings,ui.busy,{name,avatar,finished->model.action("Profile saved") {model.repository.saveProfile(name,avatar);finished()}},{nav.navigate("welcome")},{nav.navigate("wallet")})}
                composable("default-splits") {GroupDefaultsScreen(ui.snapshot,ui.busy,{value->model.action("Group default saved") {model.repository.saveGroupDefault(value)}},{nav.navigate("groups")})}
                composable("recurring") {RecurringScreen(ui.snapshot,ui.busy,{template,finished->model.action("Recurring template saved") {model.repository.saveTemplate(template);finished()}},{key->model.action("Template deleted") {model.repository.deletePlan(key)}},{template,date->model.recurringEntry(template,date);nav.navigate("add")})}
                composable("goals") {GoalsScreen(ui.snapshot,ui.busy,{name,target,id,finished->model.action("Goal saved") {model.repository.saveGoal(name,target,id);finished()}},{id,amount,finished->model.action("Planned allocation saved") {model.repository.allocateGoal(id,amount);finished()}},{key->model.action("Goal deleted") {model.repository.deletePlan(key)}})}
                composable("bill-import") {BillImportScreen(model.billScanner,model.repository,ui.snapshot,{bill,suggestion,category->model.prepareBill(bill,suggestion,category);nav.navigate("add")},::detail)}
                composable("category-rules") {CategoryRulesScreen(ui.snapshot,ui.busy,{value->model.action("Merchant rule saved") {model.repository.saveCategoryRule(value)}},{key->model.action("Merchant rule removed") {model.repository.deletePlan(key)}})}
                composable("export") {LocalExportScreen(model)}
                composable("history-import") {ChatHistoryImportScreen(model.repository,ui.snapshot) {nav.popBackStack()}}
                composable("app-lock") {AppLockSettingsScreen()}
                composable("reminders") {RemindersScreen(model)}
                composable("foreign-entry") {ManualExchangeScreen {currency,amount,rate,date,inr->
                    if(!billExpected) model.newEntry()
                    val current=model.entry.value
                    // Receipt rows and entered payments are in the original currency. Keep the
                    // receipt and original notes, but require explicit INR input before itemizing.
                    val converted=if(billExpected) current.copy(items="",billItemNames=emptyList(),itemCharges="",itemDiscount="",paid=emptyMap(),values=emptyMap(),method="",secondPayment="",secondAccountId="") else current
                    model.change(converted.copy(amount=Money.input(inr),notes=(current.notes+"\nOriginal: $currency $amount · INR per unit $rate · rate date $date").trim(),parserNotice=if(billExpected) "Total converted to INR. Re-enter item amounts, charges, discounts and actual payments in INR before splitting. Original items remain in the receipt and notes." else "Converted using your manual rate. Check the final INR amount before Save."));nav.navigate("add")
                }}
                composable("welcome") {WelcomeScreen(ui.busy) {createProfile->model.action("Welcome") {model.repository.finishWelcome();nav.navigate(if(createProfile) "profile" else "home") {popUpTo("home")}}}}
                fun androidx.navigation.NavGraphBuilder.walletRoute(path:String,addMoney:Boolean=false) {composable(path) {WalletScreen(ui.snapshot,ui.busy,
                    onSetup={opening,wallet,gpay,finished->model.action("Wallet & GPay ready") {model.repository.configureWallet(opening,wallet,gpay);finished()}},
                    onBalance={account,amount,topup,finished->model.action("Wallet balance updated") {if(topup) model.repository.addWalletFunds(account,amount) else model.repository.reconcile(account,amount);finished()}},
                    onEntry={kind,account,destination->model.newEntry(kind);model.change(model.entry.value.copy(accountId=account,destinationId=destination.orEmpty()));nav.navigate("add")},
                    onRepay={nav.navigate("repayment/all")},onAccounts={nav.navigate("accounts")},
                    onSetupManual={walletAmount,gpayAmount,wallet,gpay,finished->model.action("Wallet & GPay ready") {model.repository.configureWallet(walletAmount,wallet,gpay,gpayOpening=gpayAmount);finished()}},onGpay={nav.navigate("gpay-settings")},initialAddMoney=addMoney,onTopupDetailed={account,amount,date,note,finished->model.action("Money added") {model.repository.addWalletFunds(account,amount,date,note);finished()}},onTopupProjection={account,amount,date->model.repository.previewWalletFunds(account,amount,date)} )}}
                walletRoute("wallet");walletRoute("wallet/add-money",true)
                composable("add") {EntryScreen(form,paymentAccounts,ui.snapshot.people,ui.snapshot.categories,ui.snapshot.groups,{value->val group=ui.snapshot.groups.find {it.id==value.groupId && value.groupId!=form.groupId};model.change(if(group==null) value else model.withGroupDefault(value,group).let {if(billExpected && form.items.isNotBlank()) it.copy(items=form.items,billItemNames=form.billItemNames,method="Itemized") else it})},model::parseText,onSave={model.saveEntry {id->nav.navigate("detail/$id") {popUpTo("add") {inclusive=true}}}},records=ui.snapshot.records,onBack={nav.popBackStack()},onFriends={nav.navigate("people")},walletSources=ui.snapshot.walletSources,hasBill=billExpected,onDiscardBill=model::discardDraftBill,onExchange={nav.navigate("foreign-entry")},onImportBill={nav.navigate("bill-import")})}
                composable("split") {SplitScreen(ui.snapshot,{add(Kind.EXPENSE,true)},{nav.navigate("people")},{nav.navigate("groups")},{debt,group->nav.navigate("repayment/${debt?.id?:"all"}?group=${group.orEmpty()}")},::detail,onOffset={a,b,amount,group->model.action("Mutual offset recorded") {model.repository.recordOffset(a,b,amount,group)}},onRepeatGroup={group->model.repeatGroup(group);nav.navigate("add")},onSettings={nav.navigate("more")})}
                composable("insights") {InsightsScreen(ui.snapshot,reportMonth,model::selectMonth,onBudget={_,_,_->},onPlanner={nav.navigate("planner")},onBudgetSave={month,category,amount,finished->model.action("Budget saved") {model.repository.saveBudget(month,category,amount);finished()}},busy=ui.busy,onTransactions={nav.navigate("activity")},onPeriodReport=model.repository::report)}
                composable("budgets") {BudgetsScreen(ui.snapshot,reportMonth,model::selectMonth,{nav.popBackStack()},{month,category,amount,finished->model.action("Budget saved") {model.repository.saveBudget(month,category,amount);finished()}},ui.busy)}
                composable("planner") {MoneyPlannerScreen(ui.snapshot,onBack={nav.popBackStack()})}
                composable("calculator") {CalculatorScreen(onBack={nav.popBackStack()})}
                composable("accounts") {AccountsScreen(ui.snapshot,model,::detail,onCheck={nav.navigate("balance-check")})}
                composable("balance-check") {BalanceCheckScreen(ui.snapshot,ui.busy,::detail,{id->model.newEntry();model.change(model.entry.value.copy(accountId=id));nav.navigate("add")}) {id,target,expected,reason,done->model.action("Balance adjustment recorded") {model.repository.reconcile(id,target,expected,reason);done()}}}
                composable("categories") {CategoriesScreen(ui.snapshot,model)}
                composable("people") {PeopleScreen(ui.snapshot,model,::detail)}
                composable("groups") {GroupsScreen(ui.snapshot,model)}
                composable("backup") {BackupScreen(model)}
                composable("favourites") {FavouritesScreen(ui.snapshot,ui.busy,{value->model.favouriteEntry(value);nav.navigate("add")},{value,done->model.action("Favourite saved") {model.repository.saveFavourite(value);done()}},{id->model.action("Favourite removed") {model.repository.deletePlan("favourite:$id")}})}
                composable("repayment/{id}?group={group}",arguments=listOf(navArgument("group") {defaultValue=""})) {entry->DisposableEffect(entry) {onDispose {model.clearRepaymentDraft()}};RepaymentScreen(ui.snapshot,ui.snapshot.debts.find {it.id==entry.arguments?.getString("id")},model,repaymentDraft,entry.arguments?.getString("group")?.ifBlank {null}) {model.clearRepaymentDraft();nav.popBackStack()}}
                composable("detail/{id}") {entry->val id=entry.arguments?.getString("id").orEmpty();DetailScreen(id,ui.snapshot,model,{nav.navigate("add")}) {record ->
                    model.newEntry(Kind.REFUND);model.change(model.entry.value.copy(description="Refund: ${record.description}",categoryId=record.categoryId,refundOf=id));nav.navigate("add")
                }}
            }}
        }
    }
    replacementFavourite?.let {id->
        EditorialConfirmSheet("Replace your unfinished entry?","Your current entry and any attached receipt will be discarded. No saved transactions will change.","Discard draft and open",{replacementFavourite=null},enabled=!ui.busy) {
            replacementFavourite=null
            openFavourite(id)
        }
    }
}

@Composable fun LedgerNavigation(route:String?,onNavigate:(String)->Unit) = EditorialBottomNavigation(route,onNavigate)

private val mainTabs = setOf("home", "insights", "split")
