package net.xian.xianwalletapp.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressBookDao {
    
    @Query("SELECT * FROM address_book ORDER BY name ASC")
    fun getAllAddresses(): Flow<List<AddressBookEntity>>
    
    @Query("SELECT * FROM address_book WHERE id = :id")
    suspend fun getAddressById(id: Long): AddressBookEntity?
    
    @Query("SELECT * FROM address_book WHERE wallet_address = :address")
    suspend fun getAddressByWalletAddress(address: String): AddressBookEntity?
    
    @Query("SELECT * FROM address_book WHERE name LIKE '%' || :query || '%' OR wallet_address LIKE '%' || :query || '%'")
    fun searchAddresses(query: String): Flow<List<AddressBookEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: AddressBookEntity): Long
    
    @Update
    suspend fun updateAddress(address: AddressBookEntity)
    
    @Delete
    suspend fun deleteAddress(address: AddressBookEntity)
    
    @Query("DELETE FROM address_book WHERE id = :id")
    suspend fun deleteAddressById(id: Long)
    
    @Query("SELECT COUNT(*) FROM address_book")
    suspend fun getAddressCount(): Int
}