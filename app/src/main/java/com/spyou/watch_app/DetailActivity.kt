package com.kanha.dev
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import coil.load
import org.json.JSONObject

class DetailActivity : AppCompatActivity() {
 companion object {
  const val EXTRA_ITEM = "detail_item_json"
  fun intent(c: android.content.Context, item: JSONObject) = android.content.Intent(c, DetailActivity::class.java).putExtra(EXTRA_ITEM, item.toString())
 }
 private lateinit var data: JSONObject
 private lateinit var tabBody: LinearLayout
 private var selected = "Episodes"
 private var saved = false
 private var notified = false
 private val bg = 0xFF0B0B0F.toInt()
 private val panel = 0xFF20212A.toInt()
 private val red = 0xFFFF4D57.toInt()
 private fun dp(v:Int) = (v * resources.displayMetrics.density).toInt()
 private fun title() = data.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Anime Details"
 private fun episodeCount() = data.optInt("episodes", 0).takeIf { it > 0 } ?: 12
 private fun meta() = listOf(data.optString("year"), episodeCount().toString() + " Episodes", "Ongoing").filter { it.isNotBlank() && it != "null" }.joinToString(" · ")
 private fun text(s:String, size:Float, color:Int, bold:Boolean=false) = TextView(this).apply {
  text=s; textSize=size; setTextColor(color); includeFontPadding=false
  typeface=Typeface.create("sans-serif", if(bold) Typeface.BOLD else Typeface.NORMAL)
 }
 private fun rounded(r:Int,c:Int)=GradientDrawable().apply{cornerRadius=dp(r).toFloat();setColor(c)}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onCreate(state:Bundle?) {
  super.onCreate(state)
  data=runCatching{JSONObject(intent.getStringExtra(EXTRA_ITEM).orEmpty())}.getOrElse{JSONObject()}
  val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
  val page=LinearLayout(this).apply{orientation=1;setPadding(dp(16),0,dp(16),dp(28))}
  val hero=FrameLayout(this)
  val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)}
  val url=data.optString("banner").takeIf{it.startsWith("http")}?:data.optString("cover")
  if(url.startsWith("http")) art.load(url){allowHardware(false);crossfade(true)}
  hero.addView(art,FrameLayout.LayoutParams(-1,-1))
  hero.addView(android.view.View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x22000000,0x00000000,0xEE0B0B0F.toInt(),bg))},FrameLayout.LayoutParams(-1,-1))
  hero.addView(text("‹",38f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0x66000000)};setOnClickListener{finish()}},FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.START).apply{topMargin=dp(14)})
  hero.addView(text(title(),27f,Color.WHITE,true).apply{gravity=Gravity.CENTER;maxLines=2;setPadding(dp(12),0,dp(12),dp(16))},FrameLayout.LayoutParams(-1,dp(90),Gravity.BOTTOM))
  page.addView(hero,LinearLayout.LayoutParams(-1,dp(320)))
  page.addView(text(meta(),14f,0xFFA7A7B2.toInt()),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  fun button(s:String,white:Boolean,action:()->Unit)=text(s,16f,if(white)Color.BLACK else Color.WHITE,true).apply{gravity=Gravity.CENTER;background=rounded(8,if(white)Color.WHITE else panel);setOnClickListener{action()}}
  page.addView(button("▶   Play",true){toast("Install a source to play this title")},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(18)})
  page.addView(button("↓   Download E1",false){toast("No download source available")},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(10)})
  page.addView(text("◉   No sources installed",13f,0xFFA7A7B2.toInt()),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14);bottomMargin=dp(12)})
  val synopsis=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:"Discover the story and characters of "+title()+"."
  page.addView(text(synopsis,15f,Color.WHITE).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  page.addView(text("Read more",13f,red,true).apply{setPadding(0,dp(7),0,dp(9));setOnClickListener{toast(synopsis)}})
  val genres=data.optString("genres").takeIf{it.isNotBlank()&&it!="null"}?:"Anime"
  page.addView(text("Genres:  "+genres,13f,0xFFA7A7B2.toInt()))
  page.addView(text("Creators:  —",13f,0xFFA7A7B2.toInt()).apply{setPadding(0,dp(5),0,0)})
  val actions=LinearLayout(this).apply{gravity=Gravity.CENTER;setPadding(0,dp(20),0,dp(12))}
  val names=listOf("My List","Notify","Share","Web");val glyphs=listOf("+","♧","↗","◎")
  names.forEachIndexed{i,name->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
   val glyph=text(glyphs[i],24f,Color.WHITE);cell.addView(glyph);cell.addView(text(name,11f,0xFFA7A7B2.toInt(),true).apply{gravity=Gravity.CENTER;setPadding(0,dp(5),0,0)})
   cell.setOnClickListener{when(i){0->{saved=!saved;glyph.text=if(saved)"✓" else "+"};1->{notified=!notified;glyph.text=if(notified)"●" else "♧"};2->{val send=android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,title());startActivity(android.content.Intent.createChooser(send,"Share title"))};else->toast("No external page available")}}
   actions.addView(cell)
  }
  page.addView(actions)
  val tabs=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  listOf("Episodes","Cast","Relations","Details").forEach{name->tabs.addView(text(name,15f,if(name==selected)red else 0xFFA7A7B2.toInt(),name==selected).apply{setPadding(dp(3),dp(13),dp(18),dp(13));setOnClickListener{selected=name;refreshTabs(tabs);renderTab()}})}
  page.addView(tabs);tabBody=LinearLayout(this).apply{orientation=1};page.addView(tabBody)
  scroll.addView(page);setContentView(scroll);refreshTabs(tabs);renderTab()
 }
 private fun refreshTabs(t:LinearLayout){for(i in 0 until t.childCount){val v=t.getChildAt(i) as TextView;val active=v.text==selected;v.setTextColor(if(active)red else 0xFFA7A7B2.toInt());v.setTypeface(null,if(active)Typeface.BOLD else Typeface.NORMAL)}}
 private fun renderTab(){
  tabBody.removeAllViews()
  if(selected!="Episodes"){tabBody.addView(text(if(selected=="Details")data.optString("description").ifBlank{"No synopsis available."}else selected+" information will appear when a source provides it.",14f,0xFFA7A7B2.toInt()));return}
  tabBody.addView(text("Episodes",22f,Color.WHITE,true).apply{setPadding(0,dp(12),0,dp(8))})
  val grid=android.widget.GridLayout(this).apply{columnCount=5}
  for(n in episodeCount() downTo 1){val tile=FrameLayout(this).apply{background=rounded(10,panel);setOnClickListener{toast("Episode "+n+": connect a source to play")};addView(text(n.toString(),15f,Color.WHITE,true).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))}
   grid.addView(tile,android.widget.GridLayout.LayoutParams().apply{width=0;height=dp(56);columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f);setMargins(dp(4),dp(5),dp(4),dp(5))})}
  tabBody.addView(grid)
 }
}