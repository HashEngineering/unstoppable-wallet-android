package io.horizontalsystems.bankwallet.core

import io.horizontalsystems.bankwallet.core.utils.AddressParser
import io.horizontalsystems.bankwallet.entities.Coin
import io.horizontalsystems.bankwallet.entities.TransactionRecord
import io.horizontalsystems.ethereumkit.EthereumKit
import io.reactivex.Single
import org.web3j.crypto.Keys
import java.math.BigDecimal

class Erc20Adapter(
        coin: Coin,
        kit: EthereumKit,
        private val contractAddress: String,
        decimal: Int,
        addressParser: AddressParser) : EthereumBaseAdapter(coin, kit, decimal, addressParser) {

    init {
        ethereumKit.register(contractAddress, this)
    }

    override val balanceString: String?
        get() = ethereumKit.balanceERC20(contractAddress)

    override val balance: BigDecimal
        get() = balanceInBigDecimal(balanceString, decimal)

    override val feeCoinCode: String? = "ETH"

    override fun start() {}
    override fun clear() {}

    override fun stop() {
        ethereumKit.unregister(contractAddress)
    }

    override fun refresh() {
        ethereumKit.start()
    }

    override fun sendSingle(address: String, amount: String, feePriority: FeeRatePriority): Single<Unit> {
        return ethereumKit.sendERC20(address, contractAddress, amount, getKitFeePriority(feePriority)).map { Unit }
    }

    override fun fee(value: BigDecimal, address: String?, feePriority: FeeRatePriority): BigDecimal {
        return ethereumKit.feeERC20(getKitFeePriority(feePriority))
    }

    override fun availableBalance(address: String?, feePriority: FeeRatePriority): BigDecimal {
        return balance
    }

    override fun validate(amount: BigDecimal, address: String?, feePriority: FeeRatePriority): List<SendStateError> {
        val errors = mutableListOf<SendStateError>()
        if (amount > availableBalance(address, feePriority)) {
            errors.add(SendStateError.InsufficientAmount)
        }
        if (balanceInBigDecimal(ethereumKit.balance, decimal) < fee(amount, address, feePriority)) {
            errors.add(SendStateError.InsufficientFeeBalance)
        }
        return errors
    }

    override fun getTransactionsObservable(hashFrom: String?, limit: Int): Single<List<TransactionRecord>> {
        return ethereumKit.transactionsERC20(contractAddress, hashFrom, limit).map {
            it.map { tx -> transactionRecord(tx) }
        }
    }

    override fun onSyncStateUpdate() {
        val newState = convertState(ethereumKit.syncStateErc20(contractAddress))
        if (state != newState) {
            state = newState
        }
    }

    companion object {
        fun adapter(coin: Coin, ethereumKit: EthereumKit, contractAddress: String, decimal: Int, addressParser: AddressParser): Erc20Adapter {
            return Erc20Adapter(coin, ethereumKit, Keys.toChecksumAddress(contractAddress), decimal, addressParser)
        }
    }
}
