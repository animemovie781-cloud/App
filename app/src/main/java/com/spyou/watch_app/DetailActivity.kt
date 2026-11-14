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
  const val EXTRA_ITEM = "detail_item_json"
  fun intent(c: android.content.Context, item: JSONObject) =
   Intent(c, DetailActivity::class.java).putExtra(EXTRA_ITEM, item.toString())
 }
 private lateinit var data: JSONObject
 private lateinit var content: LinearLayout
 private lateinit var episodeArea: LinearLayout
 private var selectedTab = "Episodes"
 private var saved = false
 private var notified = false
 private var expanded = false
 private var listMode = false
 private lateinit var titleText: TextView
 private lateinit var metaText: TextView
 private lateinit var overviewText: TextView
 private lateinit var genresText: TextView
 private lateinit var creatorsText: TextView
 private lateinit var tabBar: LinearLayout
 private val bg = 0xFF0B0B0F.toInt()
 private val surface = 0xFF16161C.toInt()
 private val panel = 0xFF20212A.toInt()
 private val secondary = 0xFFA7A7B2.toInt()
 private val red = 0xFFFF4D57.toInt()
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun title()=data.optString("title").takeIf{it.isNotBlank()&&it!="null"}?:"Devils' Crest"
 private fun episodes()=data.optInt("episodes",3).coerceAtLeast(1)
 private fun isAnimeMovie() = data.optBoolean("isAnimeMovie") || (data.optString("mediaType")=="anime" && data.optString("format")=="MOVIE")
 private fun meta():String {
  val year=data.optString("year").takeIf{it.isNotBlank()&&it!="null"}?:""
  val status=data.optString("status").takeIf{it.isNotBlank()&&it!="null"}?:""
  val parts= mutableListOf<String>()
  if(year.isNotBlank()) parts.add(year)
  if(isAnimeMovie() || data.optString("mediaType")=="movie") parts.add("Movie") else if(episodes()>0) parts.add("${episodes()} Episodes")
  if(status.isNotBlank()) parts.add(status.replace("_"," ").lowercase().replaceFirstChar{it.uppercase()})
  return parts.joinToString("  ·  ")
 }
 private fun label(s:String,size:Float=15f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply{
  text=s;textSize=size;setTextColor(color);includeFontPadding=false
  typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
 }
 private fun shape(color:Int,r:Float=8f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r.toInt()).toFloat()}
 private fun image(res:Int,size:Int)=ImageView(this).apply{
  setImageResource(res);scaleType=ImageView.ScaleType.FIT_CENTER
  layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
 }
 private fun gap(height:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(height))}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  window.statusBarColor=bg;window.navigationBarColor=bg
  data=runCatching{JSONObject(intent.getStringExtra(EXTRA_ITEM).orEmpty())}.getOrElse{JSONObject()}
  val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true;clipToPadding=false}
  content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(28))}
  // Expanded 320dp artwork with bottom scrim and floating back control.
  val hero=FrameLayout(this)
  val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)}
  val banner=listOf("banner","backdrop","cover","image").firstNotNullOfOrNull{key->data.optString(key).takeIf{it.startsWith("http")}}
  if(banner!=null) art.load(banner){crossfade(true);allowHardware(false)}
  hero.addView(art,FrameLayout.LayoutParams(-1,-1))
  hero.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x22000000,0x00000000,0x660B0B0F,bg))},FrameLayout.LayoutParams(-1,-1))
  val back=FrameLayout(this).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0x66000000)};setOnClickListener{finish()}}
  back.addView(image(R.drawable.detail_back,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  hero.addView(back,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.START).apply{topMargin=dp(14);leftMargin=dp(2)})
  content.addView(hero,LinearLayout.LayoutParams(-1,dp(320)))
  // Title wordmark: use the supplied title-logo URL when metadata provides one.
  val logoUrl=listOf("logo","logoUrl","titleLogo","title_logo").firstNotNullOfOrNull{key->data.optString(key).takeIf{it.startsWith("http")}}
  titleText=label(title(),27f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END}
  if(logoUrl!=null){
   val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_START;load(logoUrl){allowHardware(false);crossfade(true)}}
   content.addView(logo,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(8)})
  }else content.addView(titleText,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  metaText=label(meta(),15f,secondary)
  content.addView(metaText,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(18)})
  content.addView(primaryButton("Play",R.drawable.detail_play,Color.WHITE,Color.BLACK){toast("Install a streaming source to play this title")})
  content.addView(gap(10))
  content.addView(primaryButton("Download E1",R.drawable.detail_download,panel,Color.WHITE){toast("No download source available")})
  val sourceRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  sourceRow.addView(image(R.drawable.detail_hub,18).apply{alpha=.55f})
  sourceRow.addView(label("No sources installed",13f,0xFF6E6E78.toInt()).apply{setPadding(dp(7),0,0,0)})
  content.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(34)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  val synopsis=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:"Sixth-grader Yuuma Ashihara and his twin sister, Sawa, are playing Actual Magic, a brand-new virtual reality MMORPG, when it suddenly begins fusing with reality."
  overviewText=label(synopsis,15.5f,secondary).apply{maxLines=3;ellipsize=TextUtils.TruncateAt.END}
  content.addView(overviewText)
  content.addView(label("Read more",13f,red,true).apply{
   setPadding(0,dp(8),0,dp(12))
   setOnClickListener{expanded=!expanded;overviewText.maxLines=if(expanded)Int.MAX_VALUE else 3;overviewText.ellipsize=if(expanded)null else TextUtils.TruncateAt.END;text=if(expanded)"Read less" else "Read more"}
  })
  val genres=data.optString("genres").takeIf{it.isNotBlank()&&it!="null"}?:"Action, Adventure, Fantasy"
  genresText=label("Genres:  $genres",13f,secondary)
  content.addView(genresText)
  creatorsText=label("Creators:  "+data.optString("creators",""),13f,secondary).apply{setPadding(0,dp(6),0,0)}
  content.addView(creatorsText)
  // Four icon actions use the exact PNG exports from the supplied kit.
  val actions=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;setPadding(0,dp(20),0,dp(12))}
  val actionCells= mutableListOf<ImageView>()
  val actionData=listOf(Triple("My List",R.drawable.detail_add,R.drawable.detail_check),Triple("Notify",R.drawable.detail_notify,R.drawable.detail_notify_active),Triple("Share",R.drawable.detail_share,R.drawable.detail_share),Triple("Web",R.drawable.detail_web,R.drawable.detail_web))
  actionData.forEachIndexed{index,item->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
   val icon=ImageView(this).apply{setImageResource(item.second);scaleType=ImageView.ScaleType.FIT_CENTER}
   actionCells.add(icon);cell.addView(icon,LinearLayout.LayoutParams(dp(24),dp(24)))
   cell.addView(label(item.first,11f,secondary,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(6),0,0)})
   cell.setOnClickListener{when(index){
    0->{saved=!saved;icon.setImageResource(if(saved)item.third else item.second)}
    1->{notified=!notified;icon.setImageResource(if(notified)item.third else item.second)}
    2->{val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,title());startActivity(Intent.createChooser(share,"Share title"))}
    3->{val url=data.optString("url");if(url.startsWith("http"))startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))else toast("No external page available")}
   }}
   actions.addView(cell)
  }
  content.addView(actions)
  tabBar=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  listOf("Episodes","Cast","Relations","Details").forEach{tab->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(22)}}
   cell.addView(label(tab,15f,if(tab==selectedTab)red else secondary,tab==selectedTab).apply{setPadding(0,dp(10),0,dp(10));setOnClickListener{selectedTab=tab;refreshTabs(tabs);renderTab()}})
   val underline=View(this).apply{tag=tab;background=shape(red,2f);visibility=if(tab==selectedTab)View.VISIBLE else View.INVISIBLE}
   cell.addView(underline,LinearLayout.LayoutParams(dp(if(tab=="Episodes")58 else tab.length*8),dp(3)))
   tabBar.addView(cell)
  }
  content.addView(tabBar)
  episodeArea=LinearLayout(this).apply{orientation=1}
  content.addView(episodeArea)
  scroll.addView(content);setContentView(scroll);renderTab();loadDetailMetadata()
 }
 private fun primaryButton(caption:String,iconId:Int,color:Int,textColor:Int,onTap:()->Unit):View{
  val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;background=shape(color,8f);setOnClickListener{onTap()}}
  row.addView(ImageView(this).apply{setImageResource(iconId);setColorFilter(textColor);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(24),dp(24)))
  row.addView(label(caption,16f,textColor,true).apply{setPadding(dp(10),0,0,0)})
  return row.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(52))}
 }
 private fun loadDetailMetadata(){
  val kind=data.optString("mediaType")
  if(kind !in setOf("anime","movie","tv")) return
  lifecycleScope.launch {
   val result=withContext(kotlinx.coroutines.Dispatchers.IO){runCatching{DetailMetadataRepository().load(data)}.getOrNull()}
   if(result==null) return@launch
   val keys=listOf("metadataSource","anilistId","malId","title","description","cover","banner","year","format","status","episodes","duration","score","genres","creators","castMembers","relationsList","isAnimeMovie")
   keys.forEach{key->if(result.has(key)) data.put(key,result.opt(key))}
   titleText.text=title();metaText.text=meta()
   overviewText.text=data.optString("description").ifBlank{"No description available."}
   genresText.text="Genres:  "+data.optString("genres")
   creatorsText.text="Creators:  "+data.optString("creators")
   if(isAnimeMovie()){
    selectedTab="Cast"
    if(tabBar.childCount>0) tabBar.getChildAt(0).visibility=View.GONE
    refreshTabs(tabBar)
   }
   renderTab()
  }
 }
 private fun refreshTabs(tabs:LinearLayout){
  for(i in 0 until tabs.childCount){val cell=tabs.getChildAt(i) as LinearLayout;val name=(cell.getChildAt(0) as TextView).text.toString();val active=name==selectedTab
   (cell.getChildAt(0) as TextView).setTextColor(if(active)red else secondary);cell.getChildAt(1).visibility=if(active)View.VISIBLE else View.INVISIBLE
  }
 }
 private fun renderTab(){
  episodeArea.removeAllViews()
  if(selectedTab!="Episodes"){
   when(selectedTab){
    "Cast" -> {
     val cast=data.optJSONArray("castMembers") ?: JSONArray()
     if(cast.length()==0) episodeArea.addView(label("No cast information available.",14f,secondary).apply{setPadding(0,dp(18),0,0)})
     for(i in 0 until cast.length()){
      val person=cast.optJSONObject(i) ?: continue
      val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(8),0,dp(8))}
      val photo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,8f)}
      val url=person.optString("image")
      if(url.startsWith("http")) photo.load(url){crossfade(true);allowHardware(false)}
      row.addView(photo,LinearLayout.LayoutParams(dp(58),dp(58)).apply{rightMargin=dp(12)})
      val text=LinearLayout(this).apply{orientation=1}
      text.addView(label(person.optString("name"),15f,Color.WHITE,true))
      val role=person.optString("role")
      if(role.isNotBlank()) text.addView(label(role,12f,secondary))
      val va=person.optString("voiceActor")
      if(va.isNotBlank()) text.addView(label("Japanese VA: $va",12f,secondary))
      row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
      episodeArea.addView(row)
     }
    }
    "Relations" -> {
     val relations=data.optJSONArray("relationsList") ?: JSONArray()
     if(relations.length()==0) episodeArea.addView(label("No related titles available.",14f,secondary).apply{setPadding(0,dp(18),0,0)})
     for(i in 0 until relations.length()){
      val item=relations.optJSONObject(i) ?: continue
      val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(7),0,dp(7))}
      val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,8f)}
      val cover=item.optString("cover")
      if(cover.startsWith("http")) art.load(cover){crossfade(true);allowHardware(false)}
      row.addView(art,LinearLayout.LayoutParams(dp(54),dp(72)).apply{rightMargin=dp(12)})
      val info=LinearLayout(this).apply{orientation=1}
      info.addView(label(item.optString("title"),14f,Color.WHITE,true))
      info.addView(label(item.optString("relation"),12f,secondary))
      row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
      episodeArea.addView(row)
     }
    }
    else -> {
     val details=listOf(
      "Source: "+data.optString("metadataSource"),
      "Format: "+data.optString("format"),
      "Status: "+data.optString("status"),
      "Episodes: "+data.optInt("episodes"),
      "Duration: "+data.optInt("duration").takeIf{it>0}?.let{"$it min"}.orEmpty(),
      "Score: "+data.optString("score"),
      "Genres: "+data.optString("genres"),
      "Studios / Creators: "+data.optString("creators"),
      data.optString("description")
     ).filter{it.isNotBlank() && !it.endsWith(": ") && !it.endsWith(": 0")}
     episodeArea.addView(label(details.joinToString("\\n\\n"),14f,secondary).apply{setPadding(0,dp(18),0,0)})
    }
   }
   return
  }
  val heading=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  heading.addView(label("Episodes",22f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  val toggle=FrameLayout(this).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)};setOnClickListener{listMode=!listMode;renderTab()}}
  toggle.addView(image(if(listMode)R.drawable.detail_grid_view else R.drawable.detail_list_view,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(toggle,LinearLayout.LayoutParams(dp(36),dp(36)).apply{rightMargin=dp(8)})
  val more=FrameLayout(this).apply{background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)};setOnClickListener{PopupMenu(this@DetailActivity,this).apply{menu.add("Newest first");menu.add("Oldest first");setOnMenuItemClickListener{toast(it.title.toString());true}}.show()}}
  more.addView(image(R.drawable.detail_more,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(more,LinearLayout.LayoutParams(dp(36),dp(36)))
  episodeArea.addView(heading,LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  if(listMode){
   for(n in episodes() downTo 1){val row=TextView(this).apply{text="Episode $n";textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,0,0);background=shape(panel,10f);setOnClickListener{toast("Episode $n: connect a source to play")}}
    episodeArea.addView(row,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(8)})}
  }else{
   val grid=GridLayout(this).apply{columnCount=5;useDefaultMargins=false}
   for(n in episodes() downTo 1){
    val tile=FrameLayout(this).apply{background=shape(panel,10f);setOnClickListener{toast("Episode $n: connect a source to play")}}
    tile.addView(label(n.toString(),15f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
    grid.addView(tile,GridLayout.LayoutParams().apply{width=0;height=dp(56);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(4),dp(5),dp(4),dp(5))})
   }
   episodeArea.addView(grid,LinearLayout.LayoutParams(-1,-2))
  }
 }
}
