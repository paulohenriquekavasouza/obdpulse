package br.com.obdpulse.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import br.com.obdpulse.R
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.LiveValue

class MetricAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onToggleFavorite: (String) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit,
) : RecyclerView.Adapter<MetricAdapter.Row>() {

    private val keys = mutableListOf<String>()
    private var values: Map<String, LiveValue> = emptyMap()
    private var favorites: Set<String> = emptySet()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = keys[position].hashCode().toLong()

    override fun getItemViewType(position: Int): Int = if (keys[position] == Keys.CONSUMPTION) TYPE_PINNED else TYPE_NORMAL

    fun submit(newKeys: List<String>, newValues: Map<String, LiveValue>, newFavorites: Set<String>) {
        values = newValues
        favorites = newFavorites
        if (keys != newKeys) {
            keys.clear()
            keys.addAll(newKeys)
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, keys.size, PAYLOAD_VALUE)
        }
    }

    fun keys(): List<String> = keys.toList()

    fun onMove(from: Int, to: Int): Boolean {
        if (from == PINNED_INDEX || to == PINNED_INDEX) return false
        keys.add(to, keys.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }

    fun persistOrder() = onOrderChanged(keys.drop(1))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_metric, parent, false)
        return Row(view)
    }

    override fun onBindViewHolder(holder: Row, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_VALUE)) {
            holder.bindValue(values[keys[position]])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        holder.bind(keys[position], values[keys[position]], keys[position] in favorites)
    }

    override fun getItemCount(): Int = keys.size

    inner class Row(view: View) : RecyclerView.ViewHolder(view) {
        private val handle: TextView = view.findViewById(R.id.handle)
        private val name: TextView = view.findViewById(R.id.name)
        private val value: TextView = view.findViewById(R.id.value)
        private val star: TextView = view.findViewById(R.id.star)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(key: String, live: LiveValue?, favorite: Boolean) {
            name.text = Labels.name(key)
            bindValue(live)
            val pinned = key == Keys.CONSUMPTION
            handle.visibility = if (pinned) View.INVISIBLE else View.VISIBLE
            star.visibility = if (pinned) View.INVISIBLE else View.VISIBLE
            star.text = if (favorite) "★" else "☆"
            star.setOnClickListener { if (!pinned) onToggleFavorite(key) }
            handle.setOnTouchListener { _, event ->
                if (!pinned && event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(this)
                false
            }
        }

        fun bindValue(live: LiveValue?) {
            value.text = live?.let { "${it.text} ${it.unit}".trim() } ?: "—"
        }
    }

    private companion object {
        const val TYPE_PINNED = 0
        const val TYPE_NORMAL = 1
        const val PINNED_INDEX = 0
        const val PAYLOAD_VALUE = "value"
    }
}

class MetricTouchCallback(
    private val adapter: MetricAdapter,
    private val onDragStateChanged: (Boolean) -> Unit,
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val draggable = viewHolder.itemViewType != 0
        val flags = if (draggable) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0
        return makeMovementFlags(flags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = target.itemViewType != 0

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        onDragStateChanged(actionState == ItemTouchHelper.ACTION_STATE_DRAG)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDragStateChanged(false)
        adapter.persistOrder()
    }
}
