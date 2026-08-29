package com.devcraft.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.devcraft.data.local.entities.OperationEntity;
import java.lang.Class;
import java.lang.Exception;
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
public final class OperationDao_Impl implements OperationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OperationEntity> __insertionAdapterOfOperationEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateOperationStatus;

  public OperationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOperationEntity = new EntityInsertionAdapter<OperationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `operations` (`operationId`,`deviceId`,`entityType`,`entityId`,`operationType`,`changedFieldsJson`,`timestamp`,`syncStatus`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OperationEntity entity) {
        statement.bindString(1, entity.getOperationId());
        statement.bindString(2, entity.getDeviceId());
        statement.bindString(3, entity.getEntityType());
        statement.bindString(4, entity.getEntityId());
        statement.bindString(5, entity.getOperationType());
        statement.bindString(6, entity.getChangedFieldsJson());
        statement.bindLong(7, entity.getTimestamp());
        statement.bindString(8, entity.getSyncStatus());
      }
    };
    this.__preparedStmtOfUpdateOperationStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE operations SET syncStatus = ? WHERE operationId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOperation(final OperationEntity operation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfOperationEntity.insert(operation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateOperationStatus(final String id, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateOperationStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfUpdateOperationStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<OperationEntity>> getPendingOperations() {
    final String _sql = "SELECT * FROM operations WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"operations"}, new Callable<List<OperationEntity>>() {
      @Override
      @NonNull
      public List<OperationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfOperationId = CursorUtil.getColumnIndexOrThrow(_cursor, "operationId");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfOperationType = CursorUtil.getColumnIndexOrThrow(_cursor, "operationType");
          final int _cursorIndexOfChangedFieldsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "changedFieldsJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSyncStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "syncStatus");
          final List<OperationEntity> _result = new ArrayList<OperationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OperationEntity _item;
            final String _tmpOperationId;
            _tmpOperationId = _cursor.getString(_cursorIndexOfOperationId);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpOperationType;
            _tmpOperationType = _cursor.getString(_cursorIndexOfOperationType);
            final String _tmpChangedFieldsJson;
            _tmpChangedFieldsJson = _cursor.getString(_cursorIndexOfChangedFieldsJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSyncStatus;
            _tmpSyncStatus = _cursor.getString(_cursorIndexOfSyncStatus);
            _item = new OperationEntity(_tmpOperationId,_tmpDeviceId,_tmpEntityType,_tmpEntityId,_tmpOperationType,_tmpChangedFieldsJson,_tmpTimestamp,_tmpSyncStatus);
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
  public Flow<List<OperationEntity>> getAllOperations() {
    final String _sql = "SELECT * FROM operations ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"operations"}, new Callable<List<OperationEntity>>() {
      @Override
      @NonNull
      public List<OperationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfOperationId = CursorUtil.getColumnIndexOrThrow(_cursor, "operationId");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfEntityType = CursorUtil.getColumnIndexOrThrow(_cursor, "entityType");
          final int _cursorIndexOfEntityId = CursorUtil.getColumnIndexOrThrow(_cursor, "entityId");
          final int _cursorIndexOfOperationType = CursorUtil.getColumnIndexOrThrow(_cursor, "operationType");
          final int _cursorIndexOfChangedFieldsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "changedFieldsJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSyncStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "syncStatus");
          final List<OperationEntity> _result = new ArrayList<OperationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OperationEntity _item;
            final String _tmpOperationId;
            _tmpOperationId = _cursor.getString(_cursorIndexOfOperationId);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpEntityType;
            _tmpEntityType = _cursor.getString(_cursorIndexOfEntityType);
            final String _tmpEntityId;
            _tmpEntityId = _cursor.getString(_cursorIndexOfEntityId);
            final String _tmpOperationType;
            _tmpOperationType = _cursor.getString(_cursorIndexOfOperationType);
            final String _tmpChangedFieldsJson;
            _tmpChangedFieldsJson = _cursor.getString(_cursorIndexOfChangedFieldsJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpSyncStatus;
            _tmpSyncStatus = _cursor.getString(_cursorIndexOfSyncStatus);
            _item = new OperationEntity(_tmpOperationId,_tmpDeviceId,_tmpEntityType,_tmpEntityId,_tmpOperationType,_tmpChangedFieldsJson,_tmpTimestamp,_tmpSyncStatus);
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
