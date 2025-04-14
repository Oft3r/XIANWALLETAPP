package net.xian.xianwalletapp.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException

/**
 * Manages saving and loading local transaction history to internal storage.
 */
class TransactionHistoryManager(private val context: Context) {

    private val gson = Gson()
    private val historyFileName = "transaction_history.json"

    /**
     * Loads the list of transaction records from the JSON file.
     * Returns an empty list if the file doesn't exist or an error occurs.
     */
    fun loadRecords(): List<LocalTransactionRecord> {
        val file = File(context.filesDir, historyFileName)
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val jsonString = file.readText()
            val type = object : TypeToken<List<LocalTransactionRecord>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: IOException) {
            Log.e("TransactionHistory", "Error loading transaction history", e)
            emptyList()
        } catch (e: Exception) { // Catch potential Gson parsing errors too
            Log.e("TransactionHistory", "Error parsing transaction history", e)
            emptyList()
        }
    }

    /**
     * Adds a new transaction record to the history and saves the updated list to the JSON file.
     */
    fun addRecord(record: LocalTransactionRecord) {
        val currentRecords = loadRecords().toMutableList()
        currentRecords.add(0, record) // Add new record to the beginning of the list

        try {
            val jsonString = gson.toJson(currentRecords)
            context.openFileOutput(historyFileName, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
            Log.d("TransactionHistory", "Transaction record added and saved.")
        } catch (e: IOException) {
            Log.e("TransactionHistory", "Error saving transaction history", e)
        } catch (e: Exception) {
            Log.e("TransactionHistory", "Error serializing transaction history", e)
        }
    }

    /**
     * Clears all transaction history. (Optional - useful for debugging or user request)
     */
    fun clearHistory() {
         try {
            val file = File(context.filesDir, historyFileName)
            if (file.exists()) {
                file.delete()
                Log.d("TransactionHistory", "Transaction history cleared.")
            }
        } catch (e: Exception) {
            Log.e("TransactionHistory", "Error clearing transaction history", e)
        }
    }
}