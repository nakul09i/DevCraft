package com.devcraft.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.devcraft.data.local.entities.OrderEntity;
import com.devcraft.data.local.entities.OrderItemEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class OrderDao_Impl implements OrderDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OrderEntity> __insertionAdapterOfOrderEntity;

  private final EntityInsertionAdapter<OrderItemEntity> __insertionAdapterOfOrderItemEntity;

  private final EntityDeletionOrUpdateAdapter<OrderEntity> __updateAdapterOfOrderEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOrderById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOrderItemsByOrderId;

  public OrderDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOrderEntity = new EntityInsertionAdapter<OrderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `orders` (`orderId`,`customerId`,`customerName`,`status`,`totalAmount`,`dueDate`,`rawMessage`,`referencesPriorOrder`,`confidence`,`needsClarification`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OrderEntity entity) {
        statement.bindString(1, entity.getOrderId());
        if (entity.getCustomerId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getCustomerId());
        }
        if (entity.getCustomerName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCustomerName());
        }
        statement.bindString(4, entity.getStatus());
        if (entity.getTotalAmount() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getTotalAmount());
        }
        if (entity.getDueDate() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDueDate());
        }
        if (entity.getRawMessage() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getRawMessage());
        }
        final int _tmp = entity.getReferencesPriorOrder() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindDouble(9, entity.getConfidence());
        final int _tmp_1 = entity.getNeedsClarification() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindLong(11, entity.getCreatedAt());
        statement.bindLong(12, entity.getUpdatedAt());
      }
    };
    this.__insertionAdapterOfOrderItemEntity = new EntityInsertionAdapter<OrderItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `order_items` (`itemId`,`orderId`,`description`,`quantity`,`unitPrice`,`attributesJson`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OrderItemEntity entity) {
        statement.bindString(1, entity.getItemId());
        statement.bindString(2, entity.getOrderId());
        statement.bindString(3, entity.getDescription());
        statement.bindLong(4, entity.getQuantity());
        if (entity.getUnitPrice() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getUnitPrice());
        }
        statement.bindString(6, entity.getAttributesJson());
      }
    };
    this.__updateAdapterOfOrderEntity = new EntityDeletionOrUpdateAdapter<OrderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `orders` SET `orderId` = ?,`customerId` = ?,`customerName` = ?,`status` = ?,`totalAmount` = ?,`dueDate` = ?,`rawMessage` = ?,`referencesPriorOrder` = ?,`confidence` = ?,`needsClarification` = ?,`createdAt` = ?,`updatedAt` = ? WHERE `orderId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OrderEntity entity) {
        statement.bindString(1, entity.getOrderId());
        if (entity.getCustomerId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getCustomerId());
        }
        if (entity.getCustomerName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCustomerName());
        }
        statement.bindString(4, entity.getStatus());
        if (entity.getTotalAmount() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getTotalAmount());
        }
        if (entity.getDueDate() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDueDate());
        }
        if (entity.getRawMessage() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getRawMessage());
        }
        final int _tmp = entity.getReferencesPriorOrder() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindDouble(9, entity.getConfidence());
        final int _tmp_1 = entity.getNeedsClarification() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindLong(11, entity.getCreatedAt());
        statement.bindLong(12, entity.getUpdatedAt());
        statement.bindString(13, entity.getOrderId());
      }
    };
    this.__preparedStmtOfDeleteOrderById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM orders WHERE orderId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOrderItemsByOrderId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM order_items WHERE orderId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOrder(final OrderEntity order, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfOrderEntity.insert(order);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertOrderItems(final List<OrderItemEntity> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfOrderItemEntity.insert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateOrder(final OrderEntity order, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfOrderEntity.handle(order);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOrderComplete(final String id, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> OrderDao.DefaultImpls.deleteOrderComplete(OrderDao_Impl.this, id, __cont), $completion);
  }

  @Override
  public Object deleteOrderById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOrderById.acquire();
        int _argIndex = 1;
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
          __preparedStmtOfDeleteOrderById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOrderItemsByOrderId(final String id,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOrderItemsByOrderId.acquire();
        int _argIndex = 1;
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
          __preparedStmtOfDeleteOrderItemsByOrderId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<OrderWithItems>> getAllOrdersWithItems() {
    final String _sql = "SELECT * FROM orders ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"order_items",
        "orders"}, new Callable<List<OrderWithItems>>() {
      @Override
      @NonNull
      public List<OrderWithItems> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfOrderId = CursorUtil.getColumnIndexOrThrow(_cursor, "orderId");
            final int _cursorIndexOfCustomerId = CursorUtil.getColumnIndexOrThrow(_cursor, "customerId");
            final int _cursorIndexOfCustomerName = CursorUtil.getColumnIndexOrThrow(_cursor, "customerName");
            final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
            final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
            final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
            final int _cursorIndexOfRawMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "rawMessage");
            final int _cursorIndexOfReferencesPriorOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "referencesPriorOrder");
            final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
            final int _cursorIndexOfNeedsClarification = CursorUtil.getColumnIndexOrThrow(_cursor, "needsClarification");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<OrderItemEntity>> _collectionItems = new ArrayMap<String, ArrayList<OrderItemEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfOrderId);
              if (!_collectionItems.containsKey(_tmpKey)) {
                _collectionItems.put(_tmpKey, new ArrayList<OrderItemEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshiporderItemsAscomDevcraftDataLocalEntitiesOrderItemEntity(_collectionItems);
            final List<OrderWithItems> _result = new ArrayList<OrderWithItems>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final OrderWithItems _item;
              final OrderEntity _tmpOrder;
              final String _tmpOrderId;
              _tmpOrderId = _cursor.getString(_cursorIndexOfOrderId);
              final String _tmpCustomerId;
              if (_cursor.isNull(_cursorIndexOfCustomerId)) {
                _tmpCustomerId = null;
              } else {
                _tmpCustomerId = _cursor.getString(_cursorIndexOfCustomerId);
              }
              final String _tmpCustomerName;
              if (_cursor.isNull(_cursorIndexOfCustomerName)) {
                _tmpCustomerName = null;
              } else {
                _tmpCustomerName = _cursor.getString(_cursorIndexOfCustomerName);
              }
              final String _tmpStatus;
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
              final Double _tmpTotalAmount;
              if (_cursor.isNull(_cursorIndexOfTotalAmount)) {
                _tmpTotalAmount = null;
              } else {
                _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
              }
              final String _tmpDueDate;
              if (_cursor.isNull(_cursorIndexOfDueDate)) {
                _tmpDueDate = null;
              } else {
                _tmpDueDate = _cursor.getString(_cursorIndexOfDueDate);
              }
              final String _tmpRawMessage;
              if (_cursor.isNull(_cursorIndexOfRawMessage)) {
                _tmpRawMessage = null;
              } else {
                _tmpRawMessage = _cursor.getString(_cursorIndexOfRawMessage);
              }
              final boolean _tmpReferencesPriorOrder;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfReferencesPriorOrder);
              _tmpReferencesPriorOrder = _tmp != 0;
              final float _tmpConfidence;
              _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
              final boolean _tmpNeedsClarification;
              final int _tmp_1;
              _tmp_1 = _cursor.getInt(_cursorIndexOfNeedsClarification);
              _tmpNeedsClarification = _tmp_1 != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpOrder = new OrderEntity(_tmpOrderId,_tmpCustomerId,_tmpCustomerName,_tmpStatus,_tmpTotalAmount,_tmpDueDate,_tmpRawMessage,_tmpReferencesPriorOrder,_tmpConfidence,_tmpNeedsClarification,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<OrderItemEntity> _tmpItemsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfOrderId);
              _tmpItemsCollection = _collectionItems.get(_tmpKey_1);
              _item = new OrderWithItems(_tmpOrder,_tmpItemsCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getOrderWithItemsById(final String id,
      final Continuation<? super OrderWithItems> $completion) {
    final String _sql = "SELECT * FROM orders WHERE orderId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, true, _cancellationSignal, new Callable<OrderWithItems>() {
      @Override
      @Nullable
      public OrderWithItems call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfOrderId = CursorUtil.getColumnIndexOrThrow(_cursor, "orderId");
            final int _cursorIndexOfCustomerId = CursorUtil.getColumnIndexOrThrow(_cursor, "customerId");
            final int _cursorIndexOfCustomerName = CursorUtil.getColumnIndexOrThrow(_cursor, "customerName");
            final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
            final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
            final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
            final int _cursorIndexOfRawMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "rawMessage");
            final int _cursorIndexOfReferencesPriorOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "referencesPriorOrder");
            final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
            final int _cursorIndexOfNeedsClarification = CursorUtil.getColumnIndexOrThrow(_cursor, "needsClarification");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<OrderItemEntity>> _collectionItems = new ArrayMap<String, ArrayList<OrderItemEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfOrderId);
              if (!_collectionItems.containsKey(_tmpKey)) {
                _collectionItems.put(_tmpKey, new ArrayList<OrderItemEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshiporderItemsAscomDevcraftDataLocalEntitiesOrderItemEntity(_collectionItems);
            final OrderWithItems _result;
            if (_cursor.moveToFirst()) {
              final OrderEntity _tmpOrder;
              final String _tmpOrderId;
              _tmpOrderId = _cursor.getString(_cursorIndexOfOrderId);
              final String _tmpCustomerId;
              if (_cursor.isNull(_cursorIndexOfCustomerId)) {
                _tmpCustomerId = null;
              } else {
                _tmpCustomerId = _cursor.getString(_cursorIndexOfCustomerId);
              }
              final String _tmpCustomerName;
              if (_cursor.isNull(_cursorIndexOfCustomerName)) {
                _tmpCustomerName = null;
              } else {
                _tmpCustomerName = _cursor.getString(_cursorIndexOfCustomerName);
              }
              final String _tmpStatus;
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
              final Double _tmpTotalAmount;
              if (_cursor.isNull(_cursorIndexOfTotalAmount)) {
                _tmpTotalAmount = null;
              } else {
                _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
              }
              final String _tmpDueDate;
              if (_cursor.isNull(_cursorIndexOfDueDate)) {
                _tmpDueDate = null;
              } else {
                _tmpDueDate = _cursor.getString(_cursorIndexOfDueDate);
              }
              final String _tmpRawMessage;
              if (_cursor.isNull(_cursorIndexOfRawMessage)) {
                _tmpRawMessage = null;
              } else {
                _tmpRawMessage = _cursor.getString(_cursorIndexOfRawMessage);
              }
              final boolean _tmpReferencesPriorOrder;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfReferencesPriorOrder);
              _tmpReferencesPriorOrder = _tmp != 0;
              final float _tmpConfidence;
              _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
              final boolean _tmpNeedsClarification;
              final int _tmp_1;
              _tmp_1 = _cursor.getInt(_cursorIndexOfNeedsClarification);
              _tmpNeedsClarification = _tmp_1 != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpOrder = new OrderEntity(_tmpOrderId,_tmpCustomerId,_tmpCustomerName,_tmpStatus,_tmpTotalAmount,_tmpDueDate,_tmpRawMessage,_tmpReferencesPriorOrder,_tmpConfidence,_tmpNeedsClarification,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<OrderItemEntity> _tmpItemsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfOrderId);
              _tmpItemsCollection = _collectionItems.get(_tmpKey_1);
              _result = new OrderWithItems(_tmpOrder,_tmpItemsCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
            _statement.release();
          }
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<OrderWithItems>> searchOrders(final String query) {
    final String _sql = "SELECT * FROM orders WHERE rawMessage LIKE '%' || ? || '%' OR customerName LIKE '%' || ? || '%' ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, query);
    _argIndex = 2;
    _statement.bindString(_argIndex, query);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"order_items",
        "orders"}, new Callable<List<OrderWithItems>>() {
      @Override
      @NonNull
      public List<OrderWithItems> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfOrderId = CursorUtil.getColumnIndexOrThrow(_cursor, "orderId");
            final int _cursorIndexOfCustomerId = CursorUtil.getColumnIndexOrThrow(_cursor, "customerId");
            final int _cursorIndexOfCustomerName = CursorUtil.getColumnIndexOrThrow(_cursor, "customerName");
            final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
            final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
            final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDate");
            final int _cursorIndexOfRawMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "rawMessage");
            final int _cursorIndexOfReferencesPriorOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "referencesPriorOrder");
            final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
            final int _cursorIndexOfNeedsClarification = CursorUtil.getColumnIndexOrThrow(_cursor, "needsClarification");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
            final ArrayMap<String, ArrayList<OrderItemEntity>> _collectionItems = new ArrayMap<String, ArrayList<OrderItemEntity>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              _tmpKey = _cursor.getString(_cursorIndexOfOrderId);
              if (!_collectionItems.containsKey(_tmpKey)) {
                _collectionItems.put(_tmpKey, new ArrayList<OrderItemEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshiporderItemsAscomDevcraftDataLocalEntitiesOrderItemEntity(_collectionItems);
            final List<OrderWithItems> _result = new ArrayList<OrderWithItems>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final OrderWithItems _item;
              final OrderEntity _tmpOrder;
              final String _tmpOrderId;
              _tmpOrderId = _cursor.getString(_cursorIndexOfOrderId);
              final String _tmpCustomerId;
              if (_cursor.isNull(_cursorIndexOfCustomerId)) {
                _tmpCustomerId = null;
              } else {
                _tmpCustomerId = _cursor.getString(_cursorIndexOfCustomerId);
              }
              final String _tmpCustomerName;
              if (_cursor.isNull(_cursorIndexOfCustomerName)) {
                _tmpCustomerName = null;
              } else {
                _tmpCustomerName = _cursor.getString(_cursorIndexOfCustomerName);
              }
              final String _tmpStatus;
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
              final Double _tmpTotalAmount;
              if (_cursor.isNull(_cursorIndexOfTotalAmount)) {
                _tmpTotalAmount = null;
              } else {
                _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
              }
              final String _tmpDueDate;
              if (_cursor.isNull(_cursorIndexOfDueDate)) {
                _tmpDueDate = null;
              } else {
                _tmpDueDate = _cursor.getString(_cursorIndexOfDueDate);
              }
              final String _tmpRawMessage;
              if (_cursor.isNull(_cursorIndexOfRawMessage)) {
                _tmpRawMessage = null;
              } else {
                _tmpRawMessage = _cursor.getString(_cursorIndexOfRawMessage);
              }
              final boolean _tmpReferencesPriorOrder;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfReferencesPriorOrder);
              _tmpReferencesPriorOrder = _tmp != 0;
              final float _tmpConfidence;
              _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
              final boolean _tmpNeedsClarification;
              final int _tmp_1;
              _tmp_1 = _cursor.getInt(_cursorIndexOfNeedsClarification);
              _tmpNeedsClarification = _tmp_1 != 0;
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpOrder = new OrderEntity(_tmpOrderId,_tmpCustomerId,_tmpCustomerName,_tmpStatus,_tmpTotalAmount,_tmpDueDate,_tmpRawMessage,_tmpReferencesPriorOrder,_tmpConfidence,_tmpNeedsClarification,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<OrderItemEntity> _tmpItemsCollection;
              final String _tmpKey_1;
              _tmpKey_1 = _cursor.getString(_cursorIndexOfOrderId);
              _tmpItemsCollection = _collectionItems.get(_tmpKey_1);
              _item = new OrderWithItems(_tmpOrder,_tmpItemsCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
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

  private void __fetchRelationshiporderItemsAscomDevcraftDataLocalEntitiesOrderItemEntity(
      @NonNull final ArrayMap<String, ArrayList<OrderItemEntity>> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, true, (map) -> {
        __fetchRelationshiporderItemsAscomDevcraftDataLocalEntitiesOrderItemEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `itemId`,`orderId`,`description`,`quantity`,`unitPrice`,`attributesJson` FROM `order_items` WHERE `orderId` IN (");
    final int _inputSize = __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : __mapKeySet) {
      _stmt.bindString(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "orderId");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfItemId = 0;
      final int _cursorIndexOfOrderId = 1;
      final int _cursorIndexOfDescription = 2;
      final int _cursorIndexOfQuantity = 3;
      final int _cursorIndexOfUnitPrice = 4;
      final int _cursorIndexOfAttributesJson = 5;
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        _tmpKey = _cursor.getString(_itemKeyIndex);
        final ArrayList<OrderItemEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final OrderItemEntity _item_1;
          final String _tmpItemId;
          _tmpItemId = _cursor.getString(_cursorIndexOfItemId);
          final String _tmpOrderId;
          _tmpOrderId = _cursor.getString(_cursorIndexOfOrderId);
          final String _tmpDescription;
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
          final int _tmpQuantity;
          _tmpQuantity = _cursor.getInt(_cursorIndexOfQuantity);
          final Double _tmpUnitPrice;
          if (_cursor.isNull(_cursorIndexOfUnitPrice)) {
            _tmpUnitPrice = null;
          } else {
            _tmpUnitPrice = _cursor.getDouble(_cursorIndexOfUnitPrice);
          }
          final String _tmpAttributesJson;
          _tmpAttributesJson = _cursor.getString(_cursorIndexOfAttributesJson);
          _item_1 = new OrderItemEntity(_tmpItemId,_tmpOrderId,_tmpDescription,_tmpQuantity,_tmpUnitPrice,_tmpAttributesJson);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
