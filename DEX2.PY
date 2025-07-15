DEX_CONTRACT = "con_dex_v2"
DEX_PAIRS = "con_pairs"

toks_to_pair = ForeignHash(foreign_contract=DEX_PAIRS, foreign_name='toks_to_pair')
pairs = ForeignHash(foreign_contract=DEX_PAIRS, foreign_name='pairs')

@export
def buy(buy_token: str, sell_token: str, amount: float, slippage: float = 1, deadline_min: float = 1):
    assert amount > 0, "Amount must be positive"
    assert slippage >= 0 and slippage <= 100, "Slippage must be between 0 and 100%"
    
    token_a, token_b = (buy_token, sell_token) if buy_token < sell_token else (sell_token, buy_token)
    
    pair_id = toks_to_pair[token_a, token_b]
    assert pair_id is not None, "Pair does not exist"
    
    dex = importlib.import_module(DEX_CONTRACT)
    dex_pairs = importlib.import_module(DEX_PAIRS)
    reserve_a, reserve_b, not_used = dex_pairs.getReserves(pair_id)
    
    if token_a == buy_token:
        reserve_buy, reserve_sell = reserve_a, reserve_b
    else:
        reserve_buy, reserve_sell = reserve_b, reserve_a
    
    numerator = amount * reserve_sell
    denominator = reserve_buy - amount
    
    assert denominator > 0, "Cannot buy more than available in reserves"
    
    input_amount = (numerator / denominator) * (1 + (slippage / 100))
    input_amount = input_amount / 0.997  # Account for 0.3% fee
    
    input_amount = input_amount * 1.0001
    
    sell_token_contract = importlib.import_module(sell_token)
    
    user_balance = sell_token_contract.balance_of(ctx.caller)
    assert user_balance >= input_amount, f"Insufficient balance: have {user_balance}, need {input_amount}"

    sell_token_contract.transfer_from(
        amount=input_amount, 
        to=ctx.this, 
        main_account=ctx.caller
    )
    
    deadline = now + datetime.timedelta(minutes=deadline_min)
    
    importlib.import_module(sell_token).approve(amount=input_amount, to=DEX_CONTRACT)
    
    output_amount = dex.swapExactTokensForTokens(
        amountIn=input_amount,
        amountOutMin=amount * (1 - (slippage / 100)),
        path=[pair_id],
        src=sell_token,
        to=ctx.caller,
        deadline=deadline
    )
    
    return input_amount, output_amount

@export
def sell(sell_token: str, buy_token: str, amount: float, slippage: float = 1, deadline_min: float = 1):
    assert amount > 0, "Amount must be positive"
    assert slippage >= 0 and slippage <= 100, "Slippage must be between 0 and 100%"
    
    token_a, token_b = (buy_token, sell_token) if buy_token < sell_token else (sell_token, buy_token)
    
    pair_id = toks_to_pair[token_a, token_b]
    assert pair_id is not None, "Pair does not exist"
    
    dex = importlib.import_module(DEX_CONTRACT)
    dex_pairs = importlib.import_module(DEX_PAIRS)
    reserve_a, reserve_b, not_used = dex_pairs.getReserves(pair_id)
    
    if token_a == sell_token:
        reserve_sell, reserve_buy = reserve_a, reserve_b
    else:
        reserve_sell, reserve_buy = reserve_b, reserve_a
    
    expected_output = dex.getAmountOut(amount, reserve_sell, reserve_buy)
    
    sell_token_contract = importlib.import_module(sell_token)
    
    # Transfer tokens from user to this contract
    sell_token_contract.transfer_from(
        amount=amount, 
        to=ctx.this, 
        main_account=ctx.caller
    )
    
    deadline = now + datetime.timedelta(minutes=deadline_min)
    
    importlib.import_module(sell_token).approve(amount=amount, to=DEX_CONTRACT)
    
    output_amount = dex.swapExactTokensForTokens(
        amountIn=amount,
        amountOutMin=expected_output * (1 - (slippage / 100)),
        path=[pair_id],
        src=sell_token,
        to=ctx.caller,
        deadline=deadline
    )
    
    return amount, output_amount