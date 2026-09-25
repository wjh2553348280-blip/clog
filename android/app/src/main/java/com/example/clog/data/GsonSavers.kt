package com.example.clog.data

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.lang.reflect.Type

/**
 * 用 Gson 序列化页面状态，配合 rememberSaveable 在旋转屏幕等配置变更时保留数据
 * （进程被杀后 Saver 不生效，页面会照常重新加载）。
 * 使用现有 Gson 依赖，不引入新库。
 */
object GsonSavers {

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    /** 非空类型的状态 Saver（列表状态用） */
    fun <T> ofType(type: Type): Saver<T, String> = object : Saver<T, String> {
        override fun SaverScope.save(value: T): String = gson.toJson(value, type)

        @Suppress("UNCHECKED_CAST")
        override fun restore(value: String): T? =
            runCatching { gson.fromJson<T>(value, type) }.getOrNull()
    }

    /** 可空类型的状态 Saver（详情/资料等可能为 null 的状态用） */
    fun <T> ofNullable(type: Type): Saver<T?, String> = object : Saver<T?, String> {
        override fun SaverScope.save(value: T?): String =
            if (value == null) "" else gson.toJson(value, type)

        @Suppress("UNCHECKED_CAST")
        override fun restore(value: String): T? =
            if (value.isEmpty()) null
            else runCatching { gson.fromJson<T>(value, type) }.getOrNull()
    }
}
