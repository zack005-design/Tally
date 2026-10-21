package com.smartexpense.data

import android.content.Context
import android.util.Log
import com.smartexpense.domain.CommandExpenseTextParser
import com.smartexpense.domain.ParsedDraft
import com.smartexpense.domain.PersonRef
import com.smartexpense.domain.ScratchCommandModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled model loaded once, with parsing and file IO kept off the UI thread. */
@Singleton
class LocalCommandParser @Inject constructor(@ApplicationContext private val context: Context) {
    private val parser by lazy {
        val model = try {
            context.assets.open("tally-command.bin").use(ScratchCommandModel::load)
        } catch (error: java.io.IOException) {
            Log.w("LocalCommandParser", "Command model unavailable; using rules", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w("LocalCommandParser", "Command model invalid; using rules", error)
            null
        }
        CommandExpenseTextParser(model)
    }

    suspend fun parse(text: String, people: List<PersonRef>): ParsedDraft =
        withContext(Dispatchers.Default) { parser.parse(text, people) }
}
