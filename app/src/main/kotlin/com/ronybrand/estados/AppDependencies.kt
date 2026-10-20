package com.ronybrand.estados

import com.ronybrand.estados.auth.AuthController
import com.ronybrand.estados.data.AskRepository
import com.ronybrand.estados.data.InfoRepository
import com.ronybrand.estados.state.StatesStore

/** What the screens need from the outside world: the app wires the real ones, the tests wire fakes. */
interface AppDependencies {
    val auth: AuthController
    val ask: AskRepository
    val states: StatesStore
    val info: InfoRepository
    val appVersion: String
}
