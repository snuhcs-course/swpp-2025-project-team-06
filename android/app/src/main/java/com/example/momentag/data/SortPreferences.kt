package com.example.momentag.data

import android.content.Context
import android.content.SharedPreferences
import com.example.momentag.viewmodel.TagSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SortPreferences
 *
 * Manages tag sort order preference using SharedPreferences
 */
@Singleton
class SortPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Get saved sort order
         */
        fun getSortOrder(): TagSortOrder {
            val orderName = prefs.getString(KEY_SORT_ORDER, TagSortOrder.CREATED_DESC.name)
            return try {
                TagSortOrder.valueOf(orderName ?: TagSortOrder.CREATED_DESC.name)
            } catch (e: IllegalArgumentException) {
                TagSortOrder.CREATED_DESC
            }
        }

        /**
         * Save sort order
         */
        fun setSortOrder(order: TagSortOrder) {
            prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
        }

        companion object {
            private const val PREFS_NAME = "sort_prefs"
            private const val KEY_SORT_ORDER = "tag_sort_order"
        }
    }
