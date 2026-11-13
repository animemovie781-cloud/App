package com.kanha.dev

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import coil.load
import org.json.JSONObject

class DetailActivity : AppCompatActivity() {
 companion object {
  const val EXTRA_ITEM="detail_item_json"
  fun intent(c:android.content.Context,item:JSONObject)=Intent(c,DetailActivity::class.java).putExtra(EXTRA_ITEM,item.toString())
 }
 private lateinit var data:JSONObject
 private lateinit var content:LinearLayout
 private lateinit var collapsedBar:LinearLayout
 private lateinit var tabs:LinearLayout
 private lateinit var tabBody:LinearLayout
 private var selectedTab="Episodes"
 private var expanded=false
 private var saved=false
 private var notified=false
 private var listMode=false
 private val bg=0xFF0B0B0F.toInt()
 private val surface=0xFF16161C.toInt()
 private val panel=0xFF20212A.toInt()
 private val muted=0xFFA7A7B2.toInt()
 private val red=0xFFFF4D57.toInt()
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun title()=data.optString("title").takeIf{it.isNotBlank()&&it!="null"}?:"Anime Details"
 private fun synopsis()=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:"No synopsis available."
 private fun episodes()=data.optInt("episodes",0).takeIf{it>0}?:12
 private fun label(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);includeFontPadding=false;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
 private fun shape(color:Int,r:Int=10,stroke:Int=0)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat();if(stroke!=0)setStroke(dp(1),stroke)}
 private fun icon(name:String,size:Int,tint:Int=Color.WHITE):ImageView {
  val v=ImageView(this);v.layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
  val id=resources.getIdentifier("detail_$name","drawable",packageName)
  if(id!=0)v.setImageResource(id) else v.setImageResource(when(name){"download"->R.drawable.ic_download;"add"->R.drawable.ic_check;"back"->R.drawable.ic_house;else->R.drawable.ic_tag})
  v.setColorFilter(tint);v.scaleType=ImageView.ScaleType.FIT_CENTER;return v
 }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  window.statusBarColor=bg;window.navigationBarColor=bg
  data=runCatching{JSONObject(intent.getStringExtra(EXTRA_ITEM).orEmpty())}.getOrElse{JSONObject()}
  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false}
  content=LinearLayout(this).apply{orientation=1;setPadding(dp(16),0,dp(16),dp(28))}
  val hero=FrameLayout(this)
  val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)}
  val image=data.optString("banner").takeIf{it.startsWith("http")}?:data.optString("cover")
  if(image.startsWith("http"))art.load(image){crossfade(true);allowHardware(false)}
  hero.addView(art,FrameLayout.LayoutParams(-1,-1))
  hero.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x22000000,0x00000000,0xAA0B0B0F.toInt(),bg))},FrameLayout.LayoutParams(-1,-1))
  hero.addView(label("‹",40f,Color.WHITE).apply{gravity=Gravity.CENTER;background=shape(0x66000000,30);setOnClickListener{finish()}},FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.START).apply{topMargin=dp(14);leftMargin=dp(-2)})
  val logo=data.optString("logo").takeIf{it.startsWith("http")}
  if(logo!=null){
   val logoView=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_START;load(logo){allowHardware(false)}}
   hero.addView(logoView,FrameLayout.LayoutParams(dp(230),dp(58),Gravity.BOTTOM or Gravity.START).apply{leftMargin=dp(0);bottomMargin=dp(12)})
  }else hero.addView(label(title(),31f,Color.WHITE,true).apply{maxLines=2;gravity=Gravity.BOTTOM or Gravity.START;setPadding(0,0,0,dp(14))},FrameLayout.LayoutParams(-1,dp(90),Gravity.BOTTOM))
  content.addView(hero,LinearLayout.LayoutParams(-1,dp(320)))
  val meta=listOf(data.optString("year"),(episodes().toString()+" Episodes"),data.optString("status").ifBlank{"Ongoing"}).filter{it.isNotBlank()&&it!="null"}.joinToString("  ·  ")
  content.addView(label(meta,15f,muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10);bottomMargin=dp(18)})
  content.addView(actionButton("Play",true,"play"){toast("Install a streaming source to play this title")},LinearLayout.LayoutParams(-1,dp(52)))
  content.addView(actionButton("Download E1",false,"download"){toast("No download source available")},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(10)})
  val sourceRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  sourceRow.addView(icon("hub",20,0xFF777783.toInt()))
  sourceRow.addView(label(if(data.optBoolean("hasSources"))"Sources available" else "No sources installed",13f,0xFF777783.toInt()).apply{setPadding(dp(7),0,0,0)})
  content.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(38)).apply{topMargin=dp(4);bottomMargin=dp(10)})
  val description=label(synopsis(),15.5f,muted).apply{maxLines=3;ellipsize=TextUtils.TruncateAt.END}
  content.addView(description)
  content.addView(label("Read more",13f,red,true).apply{setPadding(0,dp(8),0,dp(12));setOnClickListener{expanded=!expanded;description.maxLines=if(expanded)Int.MAX_VALUE else 3;description.ellipsize=if(expanded)null else TextUtils.TruncateAt.END;text=if(expanded)"Read less" else "Read more"}})
  content.addView(label("Genres:  "+data.optString("genres").ifBlank{"—"},14f,muted))
  content.addView(label("Creators:  "+data.optString("creators").ifBlank{"—"},14f,muted).apply{setPadding(0,dp(6),0,0)})
  val actions=LinearLayout(this).apply{gravity=Gravity.CENTER;setPadding(0,dp(22),0,dp(12))}
  val actionNames=listOf("My List","Notify","Share","Web")
  val actionIcons=listOf("add","notify","share","web")
  actionNames.forEachIndexed{i,n->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
   val iv=icon(actionIcons[i],24)
   cell.addView(iv);cell.addView(label(n,11f,muted,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(6),0,0)})
   cell.setOnClickListener{when(i){0->{saved=!saved;iv.setImageResource(resources.getIdentifier(if(saved)"detail_check" else "detail_add","drawable",packageName));toast(if(saved)"Added to My List" else "Removed from My List")};1->{notified=!notified;iv.setImageResource(resources.getIdentifier(if(notified)"detail_notify_active" else "detail_notify","drawable",packageName));toast(if(notified)"Notifications enabled" else "Notifications disabled")};2->{startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,title()),"Share"))};else->{val u=data.optString("url");if(u.startsWith("http"))startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(u)))else toast("No web link available")}}}
   actions.addView(cell)
  }
  content.addView(actions)
  tabs=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  listOf("Episodes","Cast","Relations","Details").forEach{tab->tabs.addView(tabView(tab))}
  content.addView(tabs)
  tabBody=LinearLayout(this).apply{orientation=1}
  content.addView(tabBody)
  scroll.addView(content)
  root.addView(scroll,FrameLayout.LayoutParams(-1,-1))
  collapsedBar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(8),dp(16),dp(8));setBackgroundColor(bg);visibility=View.GONE}
  val back=label("‹",36f,Color.WHITE).apply{gravity=Gravity.CENTER;setOnClickListener{finish()}}
  collapsedBar.addView(back,LinearLayout.LayoutParams(dp(42),dp(42)))
  collapsedBar.addView(label(title(),18f,Color.WHITE,true).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END;setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(0,dp(42),1f))
  root.addView(collapsedBar,FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP))
  scroll.setOnScrollChangeListener{_,_,y,_,_->collapsedBar.visibility=if(y>dp(245))View.VISIBLE else View.GONE}
  setContentView(root)
  renderTab()
 }
 private fun actionButton(text:String,primary:Boolean,iconName:String,click:()->Unit):View{
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER;orientation=0;background=shape(if(primary)Color.WHITE else panel,8);setOnClickListener{click()}}
  row.addView(icon(iconName,24,if(primary)Color.BLACK else Color.WHITE))
  row.addView(label(text,16f,if(primary)Color.BLACK else Color.WHITE,true).apply{setPadding(dp(10),0,0,0)})
  return row
 }
 private fun tabView(name:String)=label(name,15f,if(name==selectedTab)red else muted,name==selectedTab).apply{
  setPadding(dp(3),dp(14),dp(22),dp(13))
  setOnClickListener{selectedTab=name;for(i in 0 until tabs.childCount){val v=tabs.getChildAt(i) as TextView;val on=v.text==selectedTab;v.setTextColor(if(on)red else muted);v.typeface=Typeface.create("sans-serif",if(on)Typeface.BOLD else Typeface.NORMAL)};renderTab()}
 }
 private fun renderTab(){
  tabBody.removeAllViews()
  when(selectedTab){
   "Episodes"->{
    val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    head.addView(label("Episodes",22f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
    val toggle=ImageView(this).apply{setImageResource(resources.getIdentifier(if(listMode)"detail_grid_view" else "detail_list_view","drawable",packageName));setColorFilter(Color.WHITE);setPadding(dp(8),dp(8),dp(8),dp(8));background=shape(panel,24);setOnClickListener{listMode=!listMode;renderTab()}}
    head.addView(toggle,LinearLayout.LayoutParams(dp(36),dp(36)).apply{marginEnd=dp(8)})
    val more=ImageView(this).apply{setImageResource(resources.getIdentifier("detail_more","drawable",packageName));setColorFilter(Color.WHITE);setPadding(dp(8),dp(8),dp(8),dp(8));background=shape(panel,24);setOnClickListener{PopupMenu(this@DetailActivity,this).apply{menu.add("Reverse episode order");menu.add("Refresh episodes");setOnMenuItemClickListener{toast(it.title.toString());true};show()}}}
    head.addView(more,LinearLayout.LayoutParams(dp(36),dp(36)))
    tabBody.addView(head,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(6);bottomMargin=dp(10)})
    if(listMode){for(n in episodes() downTo 1){val row=label("Episode $n",15f,Color.WHITE,true).apply{background=shape(panel,10);setPadding(dp(16),dp(17),dp(16),dp(17));setOnClickListener{toast("Connect a source to play episode $n")}};tabBody.addView(row,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(8)})}}
    else{
     val grid=GridLayout(this).apply{columnCount=5}
     for(n in episodes() downTo 1){val tile=FrameLayout(this).apply{background=shape(panel,10);setOnClickListener{toast("Connect a source to play episode $n")};addView(label(n.toString(),15f,Color.WHITE,true).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))}
      grid.addView(tile,GridLayout.LayoutParams().apply{width=0;height=dp(56);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(3),dp(4),dp(3),dp(4))})}
     tabBody.addView(grid)
    }
   }
   "Cast"->tabBody.addView(label(data.optString("cast").ifBlank{"No cast information available."},14f,muted).apply{setPadding(0,dp(12),0,0)})
   "Relations"->tabBody.addView(label("Related titles will appear when metadata provides them.",14f,muted).apply{setPadding(0,dp(12),0,0)})
   else->{tabBody.addView(label("Title:  "+title(),14f,muted).apply{setPadding(0,dp(12),0,dp(8))});tabBody.addView(label("Year:  "+data.optString("year").ifBlank{"—"},14f,muted));tabBody.addView(label("Status:  "+data.optString("status").ifBlank{"—"},14f,muted).apply{setPadding(0,dp(8),0,0)})}
  }
 }
}
