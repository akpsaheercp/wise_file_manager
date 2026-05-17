# Room
-keep class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao
-keep class com.wise.file_manager.db.*_Impl { *; }

# Image processing Coil
-keep class coil.transform.BlurTransformation { *; }