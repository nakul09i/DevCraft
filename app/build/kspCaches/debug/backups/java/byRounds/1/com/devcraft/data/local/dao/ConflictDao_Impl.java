package com.devcraft.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.devcraft.data.local.entities.ConflictEntity;
import java.lang.Class;
import java.lang.Exception;
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
public final class ConflictDao_Impl implements ConflictDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConflictEntity> __insertionAdapterOfConflictEntity;

  public ConflictDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConflictEntity = new EntityInsertionAdapter<ConflictEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `conflicts` (`conflictId`,`entityId`,`entityType`,`field`,`localValue`,`remoteValue`,`winningValue`,`resolutionReason`,`createdAt`,`resolvedAt`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConflictEntity entity) {
        statement.bindString(1, entity.getConflictId());
        statement.bindString(2, entity.getEntityId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getField());
        if (entity.getLocalValue() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLocalValue());
        }
        if (entity.getRemoteValue() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRemoteValue());
        }
        if (entity.getWinningValue() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getWinningValue());
        }
        statement.bindString(8, entity.getResolutionReason());
        statement.bindLong(9, entity.getCreatedAt());
        if (entity.getResolvedAt() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getResolvedAt());
        }
      }
    };
  }

  @Override
  public Object insertConflict(final ConflictEntity conflict,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConflictEntity.insert(conflict);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ConflictEntity>> getAllConflicts() {
    final String _sql = "SELECT * FROM conflicts ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"conflicts"}, new Callable<List<ConflictEntity>>() {
      @Override
      @NonNull
      public List<ConflictEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfConflictId = CursorUtil.getColumnIndexOrThrow(_cursor, "conflictId");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfField = CursorUtil.getColumnIndexOrThrow(_cursor, "field");
          final int _cursorIndexOfLocalValue = CursorUtil.getColumnIndexOrThrow(_cursor, "localValue");
          final int _cursorIndexOfRemoteValue = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteValue");
          final int _cursorIndexOfWinningValue = CursorUtil.getColumnIndexOrThrow(_cursor, "winningValue");
          final int _cursorIndexOfResolutionReason = CursorUtil.getColumnIndexOrThrow(_cursor, "resolutionReason");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfResolvedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "resolvedAt");
          final List<ConflictEntity> _result = new ArrayList<ConflictEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConflictEntity _item;
            final String _tmpConflictId;
            _tmpConflictId = _cursor.getString(_cursorIndexOfConflictId);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpField;
            _tmpField = _cursor.getString(_cursorIndexOfField);
            final String _tmpLocalValue;
            if (_cursor.isNull(_cursorIndexOfLocalValue)) {
              _tmpLocalValue = null;
            } else {
              _tmpLocalValue = _cursor.getString(_cursorIndexOfLocalValue);
            }
            final String _tmpRemoteValue;
            if (_cursor.isNull(_cursorIndexOfRemoteValue)) {
              _tmpRemoteValue = null;
            } else {
              _tmpRemoteValue = _cursor.getString(_cursorIndexOfRemoteValue);
            }
            final String _tmpWinningValue;
            if (_cursor.isNull(_cursorIndexOfWinningValue)) {
              _tmpWinningValue = null;
            } else {
              _tmpWinningValue = _cursor.getString(_cursorIndexOfWinningValue);
            }
            final String _tmpResolutionReason;
            _tmpResolutionReason = _cursor.getString(_cursorIndexOfResolutionReason);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpResolvedAt;
            if (_cursor.isNull(_cursorIndexOfResolvedAt)) {
              _tmpResolvedAt = null;
            } else {
              _tmpResolvedAt = _cursor.getLong(_cursorIndexOfResolvedAt);
            }
            _item = new ConflictEntity(_tmpConflictId,_tmpEntityId,_tmpEntityType,_tmpField,_tmpLocalValue,_tmpRemoteValue,_tmpWinningValue,_tmpResolutionReason,_tmpCreatedAt,_tmpResolvedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
