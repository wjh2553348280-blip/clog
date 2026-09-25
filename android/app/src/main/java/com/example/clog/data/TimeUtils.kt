package com.example.clog.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 时间显示工具：后端时间是 UTC 的 ISO 字符串 */
object TimeUtils {

    private val isoFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    private val timeFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.CHINA) }
    private val shortDateFmt = ThreadLocal.withInitial { SimpleDateFormat("M月d日", Locale.CHINA) }
    private val fullDateFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }

    /** 解析后端的 ISO 时间（UTC）为本地时间戳 */
    fun parseToLocal(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            isoFmt.get()!!.parse(iso.take(19))?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /** 两个时间戳相差的自然日数（按本地零点对齐） */
    private fun daysBetween(from: Long, to: Long): Int {
        val c1 = Calendar.getInstance().apply { timeInMillis = from }
        val c2 = Calendar.getInstance().apply { timeInMillis = to }
        val zero1 = Calendar.getInstance().apply {
            timeInMillis = c1.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val zero2 = Calendar.getInstance().apply {
            timeInMillis = c2.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return ((zero2.timeInMillis - zero1.timeInMillis) / 86_400_000L).toInt()
    }

    /** 相对时间：刚刚 / 5分钟前 / 3小时前 / 昨天 14:32 / 2天前 / 9月1日 / 2025年9月1日 */
    fun relative(iso: String?): String {
        val t = parseToLocal(iso)
        val now = System.currentTimeMillis()
        val diff = now - t
        val minutes = diff / 60_000
        return when {
            diff < 60_000 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 60 * 24 -> "${minutes / 60}小时前"
            daysBetween(t, now) == 1 -> "昨天 ${timeFmt.get()!!.format(Date(t))}"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}天前"
            else -> {
                val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
                        Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
                if (sameYear) shortDateFmt.get()!!.format(Date(t))
                else fullDateFmt.get()!!.format(Date(t))
            }
        }
    }
}
