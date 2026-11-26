# PKMAPP (芽叶记) R8 / ProGuard 规则
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Room 数据库、Entity、DAO 与自动生成实现类
-keep class com.example.pkmapp.core.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager 反射构造的后台 Worker
-keep class com.example.pkmapp.core.work.ReminderRescheduleWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.pkmapp.core.work.SyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# XML 布局反射实例化的自定义 View
-keep public class com.example.pkmapp.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 动态资源解析（Resources.getIdentifier("norm_type_...", "drawable", ...)）
-keep class com.example.pkmapp.record.CategoryIconResolver { *; }

# 枚举反射与序列化安全保留
-keepclassmembers enum com.example.pkmapp.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
