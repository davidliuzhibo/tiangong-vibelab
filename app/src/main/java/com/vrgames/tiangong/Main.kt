package com.vrgames.tiangong

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.tiangong.content.BookWindow
import com.vrgames.tiangong.content.OrdersWindow
import com.vrgames.tiangong.content.WorkshopStage

const val ORDERS_ID = "Orders"
const val BOOK_ID = "Book"

/**
 * Three linked spaces: the workshop on the player's table (a mixed-reality Stage), the 订单簿
 * (orders, stars, trophies) and the 天工开物图谱 (the book's chapters). They share
 * [com.vrgames.tiangong.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { WorkshopStage() }
        }
        WindowContainer(ORDERS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { OrdersWindow() }
        }
        WindowContainer(BOOK_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { BookWindow() }
        }
    }
