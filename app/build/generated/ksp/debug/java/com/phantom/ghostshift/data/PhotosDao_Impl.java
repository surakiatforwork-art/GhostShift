package com.phantom.ghostshift.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.phantom.ghostshift.domain.Kind;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PhotosDao_Impl implements PhotosDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<PhotoEntity> __deletionAdapterOfPhotoEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final EntityUpsertionAdapter<PhotoEntity> __upsertionAdapterOfPhotoEntity;

  public PhotosDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfPhotoEntity = new EntityDeletionOrUpdateAdapter<PhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `photos` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photos";
        return _query;
      }
    };
    this.__upsertionAdapterOfPhotoEntity = new EntityUpsertionAdapter<PhotoEntity>(new EntityInsertionAdapter<PhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `photos` (`id`,`tag`,`kind`,`idx`,`createdAt`,`downloaded`,`downloadedAt`,`editedAt`,`filePath`,`width`,`height`,`mime`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTag());
        statement.bindString(3, __Kind_enumToString(entity.getKind()));
        statement.bindLong(4, entity.getIdx());
        statement.bindLong(5, entity.getCreatedAt());
        final int _tmp = entity.getDownloaded() ? 1 : 0;
        statement.bindLong(6, _tmp);
        if (entity.getDownloadedAt() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getDownloadedAt());
        }
        if (entity.getEditedAt() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getEditedAt());
        }
        statement.bindString(9, entity.getFilePath());
        statement.bindLong(10, entity.getWidth());
        statement.bindLong(11, entity.getHeight());
        statement.bindString(12, entity.getMime());
      }
    }, new EntityDeletionOrUpdateAdapter<PhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `photos` SET `id` = ?,`tag` = ?,`kind` = ?,`idx` = ?,`createdAt` = ?,`downloaded` = ?,`downloadedAt` = ?,`editedAt` = ?,`filePath` = ?,`width` = ?,`height` = ?,`mime` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTag());
        statement.bindString(3, __Kind_enumToString(entity.getKind()));
        statement.bindLong(4, entity.getIdx());
        statement.bindLong(5, entity.getCreatedAt());
        final int _tmp = entity.getDownloaded() ? 1 : 0;
        statement.bindLong(6, _tmp);
        if (entity.getDownloadedAt() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getDownloadedAt());
        }
        if (entity.getEditedAt() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getEditedAt());
        }
        statement.bindString(9, entity.getFilePath());
        statement.bindLong(10, entity.getWidth());
        statement.bindLong(11, entity.getHeight());
        statement.bindString(12, entity.getMime());
        statement.bindLong(13, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final PhotoEntity photo, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfPhotoEntity.handle(photo);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final PhotoEntity photo, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __upsertionAdapterOfPhotoEntity.upsertAndReturnId(photo);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PhotoEntity>> getAllSortedByIdx() {
    final String _sql = "SELECT * FROM photos ORDER BY idx ASC, kind ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"photos"}, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTag = CursorUtil.getColumnIndexOrThrow(_cursor, "tag");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDownloaded = CursorUtil.getColumnIndexOrThrow(_cursor, "downloaded");
          final int _cursorIndexOfDownloadedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedAt");
          final int _cursorIndexOfEditedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "editedAt");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTag;
            _tmpTag = _cursor.getString(_cursorIndexOfTag);
            final Kind _tmpKind;
            _tmpKind = __Kind_stringToEnum(_cursor.getString(_cursorIndexOfKind));
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpDownloaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDownloaded);
            _tmpDownloaded = _tmp != 0;
            final Long _tmpDownloadedAt;
            if (_cursor.isNull(_cursorIndexOfDownloadedAt)) {
              _tmpDownloadedAt = null;
            } else {
              _tmpDownloadedAt = _cursor.getLong(_cursorIndexOfDownloadedAt);
            }
            final Long _tmpEditedAt;
            if (_cursor.isNull(_cursorIndexOfEditedAt)) {
              _tmpEditedAt = null;
            } else {
              _tmpEditedAt = _cursor.getLong(_cursorIndexOfEditedAt);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final String _tmpMime;
            _tmpMime = _cursor.getString(_cursorIndexOfMime);
            _item = new PhotoEntity(_tmpId,_tmpTag,_tmpKind,_tmpIdx,_tmpCreatedAt,_tmpDownloaded,_tmpDownloadedAt,_tmpEditedAt,_tmpFilePath,_tmpWidth,_tmpHeight,_tmpMime);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<PhotoEntity>> getPendingSorted() {
    final String _sql = "SELECT * FROM photos WHERE downloaded = 0 ORDER BY idx ASC, kind ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"photos"}, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTag = CursorUtil.getColumnIndexOrThrow(_cursor, "tag");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDownloaded = CursorUtil.getColumnIndexOrThrow(_cursor, "downloaded");
          final int _cursorIndexOfDownloadedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedAt");
          final int _cursorIndexOfEditedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "editedAt");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTag;
            _tmpTag = _cursor.getString(_cursorIndexOfTag);
            final Kind _tmpKind;
            _tmpKind = __Kind_stringToEnum(_cursor.getString(_cursorIndexOfKind));
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpDownloaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDownloaded);
            _tmpDownloaded = _tmp != 0;
            final Long _tmpDownloadedAt;
            if (_cursor.isNull(_cursorIndexOfDownloadedAt)) {
              _tmpDownloadedAt = null;
            } else {
              _tmpDownloadedAt = _cursor.getLong(_cursorIndexOfDownloadedAt);
            }
            final Long _tmpEditedAt;
            if (_cursor.isNull(_cursorIndexOfEditedAt)) {
              _tmpEditedAt = null;
            } else {
              _tmpEditedAt = _cursor.getLong(_cursorIndexOfEditedAt);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final String _tmpMime;
            _tmpMime = _cursor.getString(_cursorIndexOfMime);
            _item = new PhotoEntity(_tmpId,_tmpTag,_tmpKind,_tmpIdx,_tmpCreatedAt,_tmpDownloaded,_tmpDownloadedAt,_tmpEditedAt,_tmpFilePath,_tmpWidth,_tmpHeight,_tmpMime);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<PhotoEntity>> getDownloadedSorted() {
    final String _sql = "SELECT * FROM photos WHERE downloaded = 1 ORDER BY downloadedAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"photos"}, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTag = CursorUtil.getColumnIndexOrThrow(_cursor, "tag");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDownloaded = CursorUtil.getColumnIndexOrThrow(_cursor, "downloaded");
          final int _cursorIndexOfDownloadedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedAt");
          final int _cursorIndexOfEditedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "editedAt");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTag;
            _tmpTag = _cursor.getString(_cursorIndexOfTag);
            final Kind _tmpKind;
            _tmpKind = __Kind_stringToEnum(_cursor.getString(_cursorIndexOfKind));
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpDownloaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDownloaded);
            _tmpDownloaded = _tmp != 0;
            final Long _tmpDownloadedAt;
            if (_cursor.isNull(_cursorIndexOfDownloadedAt)) {
              _tmpDownloadedAt = null;
            } else {
              _tmpDownloadedAt = _cursor.getLong(_cursorIndexOfDownloadedAt);
            }
            final Long _tmpEditedAt;
            if (_cursor.isNull(_cursorIndexOfEditedAt)) {
              _tmpEditedAt = null;
            } else {
              _tmpEditedAt = _cursor.getLong(_cursorIndexOfEditedAt);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final String _tmpMime;
            _tmpMime = _cursor.getString(_cursorIndexOfMime);
            _item = new PhotoEntity(_tmpId,_tmpTag,_tmpKind,_tmpIdx,_tmpCreatedAt,_tmpDownloaded,_tmpDownloadedAt,_tmpEditedAt,_tmpFilePath,_tmpWidth,_tmpHeight,_tmpMime);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final long id, final Continuation<? super PhotoEntity> $completion) {
    final String _sql = "SELECT * FROM photos WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PhotoEntity>() {
      @Override
      @Nullable
      public PhotoEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTag = CursorUtil.getColumnIndexOrThrow(_cursor, "tag");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfIdx = CursorUtil.getColumnIndexOrThrow(_cursor, "idx");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfDownloaded = CursorUtil.getColumnIndexOrThrow(_cursor, "downloaded");
          final int _cursorIndexOfDownloadedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadedAt");
          final int _cursorIndexOfEditedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "editedAt");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final PhotoEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTag;
            _tmpTag = _cursor.getString(_cursorIndexOfTag);
            final Kind _tmpKind;
            _tmpKind = __Kind_stringToEnum(_cursor.getString(_cursorIndexOfKind));
            final int _tmpIdx;
            _tmpIdx = _cursor.getInt(_cursorIndexOfIdx);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpDownloaded;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDownloaded);
            _tmpDownloaded = _tmp != 0;
            final Long _tmpDownloadedAt;
            if (_cursor.isNull(_cursorIndexOfDownloadedAt)) {
              _tmpDownloadedAt = null;
            } else {
              _tmpDownloadedAt = _cursor.getLong(_cursorIndexOfDownloadedAt);
            }
            final Long _tmpEditedAt;
            if (_cursor.isNull(_cursorIndexOfEditedAt)) {
              _tmpEditedAt = null;
            } else {
              _tmpEditedAt = _cursor.getLong(_cursorIndexOfEditedAt);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final String _tmpMime;
            _tmpMime = _cursor.getString(_cursorIndexOfMime);
            _result = new PhotoEntity(_tmpId,_tmpTag,_tmpKind,_tmpIdx,_tmpCreatedAt,_tmpDownloaded,_tmpDownloadedAt,_tmpEditedAt,_tmpFilePath,_tmpWidth,_tmpHeight,_tmpMime);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __Kind_enumToString(@NonNull final Kind _value) {
    switch (_value) {
      case IN: return "IN";
      case OUT: return "OUT";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private Kind __Kind_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "IN": return Kind.IN;
      case "OUT": return Kind.OUT;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
