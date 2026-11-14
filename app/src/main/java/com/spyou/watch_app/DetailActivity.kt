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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 private var selectedSeason = 1
 private var selectedEpisode = 1
 private var tmdbLoading = false
 private var tmdbError: String? = null
 private var tmdbBundle: TmdbDetailBundle? = null
 private val bg = 0xFF0B0B0F.toInt()
 private val surface = 0xFF16161C.toInt()
 private val panel = 0xFF20212A.toInt()
 private val secondary = 0xFFA7A7B2.toInt()
 private val red = 0xFFFF4D57.toInt()
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun title()=data.optString("title").takeIf{it.isNotBlank()&&it!="null"}?:"Devils' Crest"
 private fun episodes()=data.optInt("episodes",3).coerceAtLeast(1)
 private fun meta():String {
  val year=data.optString("year").takeIf{it.isNotBlank()&&it!="null"}?:"2026"
  val status=data.optString("status").takeIf{it.isNotBlank()&&it!="null"}?:"Ongoing"
  return "$year  ·  ${episodes()} Episodes  ·  $status"
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
  val titleView=label(title(),27f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END}
  if(logoUrl!=null){
   val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_START;load(logoUrl){allowHardware(false);crossfade(true)}}
   content.addView(logo,LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(8)})
  }else content.addView(titleView,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  content.addView(label(meta(),15f,secondary),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(18)})
  content.addView(primaryButton("Play",R.drawable.detail_play,Color.WHITE,Color.BLACK){toast("Install a streaming source to play this title")})
  content.addView(gap(10))
  content.addView(primaryButton("Download E1",R.drawable.detail_download,panel,Color.WHITE){toast("No download source available")})
  val sourceRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  sourceRow.addView(image(R.drawable.detail_hub,18).apply{alpha=.55f})
  sourceRow.addView(label("No sources installed",13f,0xFF6E6E78.toInt()).apply{setPadding(dp(7),0,0,0)})
  content.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(34)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  val synopsis=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:"Sixth-grader Yuuma Ashihara and his twin sister, Sawa, are playing Actual Magic, a brand-new virtual reality MMORPG, when it suddenly begins fusing with reality."
  val overview=label(synopsis,15.5f,secondary).apply{maxLines=3;ellipsize=TextUtils.TruncateAt.END}
  content.addView(overview)
  content.addView(label("Read more",13f,red,true).apply{
   setPadding(0,dp(8),0,dp(12))
   setOnClickListener{expanded=!expanded;overview.maxLines=if(expanded)Int.MAX_VALUE else 3;overview.ellipsize=if(expanded)null else TextUtils.TruncateAt.END;text=if(expanded)"Read less" else "Read more"}
  })
  val genres=data.optString("genres").takeIf{it.isNotBlank()&&it!="null"}?:"Action, Adventure, Fantasy"
  content.addView(label("Genres:  $genres",13f,secondary))
  content.addView(label("Creators:  "+data.optString("creators","Production I.G"),13f,secondary).apply{setPadding(0,dp(6),0,0)})
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
  val tabs=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  listOf("Episodes","Cast","Relations","Details").forEach{tab->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(22)}}
   cell.addView(label(tab,15f,if(tab==selectedTab)red else secondary,tab==selectedTab).apply{setPadding(0,dp(10),0,dp(10));setOnClickListener{selectedTab=tab;refreshTabs(tabs);renderTab()}})
   val underline=View(this).apply{tag=tab;background=shape(red,2f);visibility=if(tab==selectedTab)View.VISIBLE else View.INVISIBLE}
   cell.addView(underline,LinearLayout.LayoutParams(dp(if(tab=="Episodes")58 else tab.length*8),dp(3)))
   tabs.addView(cell)
  }
  content.addView(tabs)
  episodeArea=LinearLayout(this).apply{orientation=1}
  content.addView(episodeArea)
  scroll.addView(content);setContentView(scroll);renderTab();loadTmdbDetails()
 }
 private fun primaryButton(caption:String,iconId:Int,color:Int,textColor:Int,onTap:()->Unit):View{
  val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER;background=shape(color,8f);setOnClickListener{onTap()}}
  row.addView(ImageView(this).apply{setImageResource(iconId);setColorFilter(textColor);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(24),dp(24)))
  row.addView(label(caption,16f,textColor,true).apply{setPadding(dp(10),0,0,0)})
  return row.apply{layoutParams=LinearLayout.LayoutParams(-1,dp(52))}
 }
 private fun refreshTabs(tabs:LinearLayout){
  for(i in 0 until tabs.childCount){val cell=tabs.getChildAt(i) as LinearLayout;val name=(cell.getChildAt(0) as TextView).text.toString();val active=name==selectedTab
   (cell.getChildAt(0) as TextView).setTextColor(if(active)red else secondary);cell.getChildAt(1).visibility=if(active)View.VISIBLE else View.INVISIBLE
  }
 }
 private fun renderTab(){
  episodeArea.removeAllViews()
  when(selectedTab){
   "Cast" -> renderCastTab()
   "Relations" -> renderRelationsTab()
   "Details" -> renderDetailsTab()
   else -> renderEpisodesTab()
  }
 }

 private fun renderEpisodesTab(){
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
   for(n in episodes() downTo 1){
    val row=TextView(this).apply{
     text="Episode $n";textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL
     setPadding(dp(16),0,0,0);background=shape(if(n==selectedEpisode)0xFF343541.toInt() else panel,10f)
     setOnClickListener{selectedEpisode=n;renderTab();if(selectedTab=="Cast")loadTmdbDetails()}
    }
    episodeArea.addView(row,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(8)})
   }
  }else{
   val grid=GridLayout(this).apply{columnCount=5;useDefaultMargins=false}
   for(n in episodes() downTo 1){
    val tile=FrameLayout(this).apply{
     background=shape(if(n==selectedEpisode)0xFF343541.toInt() else panel,10f)
     setOnClickListener{selectedEpisode=n;if(selectedTab=="Cast")loadTmdbDetails();renderTab()}
    }
    tile.addView(label(n.toString(),15f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
    grid.addView(tile,GridLayout.LayoutParams().apply{width=0;height=dp(56);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(4),dp(5),dp(4),dp(5))})
   }
   episodeArea.addView(grid,LinearLayout.LayoutParams(-1,-2))
  }
 }

 private fun renderCastTab(){
  val heading=label("Episode $selectedEpisode Cast",20f,Color.WHITE,true)
  episodeArea.addView(heading,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(12)})
  if(tmdbLoading){episodeArea.addView(label("Loading cast from TMDB…",14f,secondary));return}
  val cast=tmdbBundle?.cast.orEmpty()
  if(cast.isEmpty()){
   episodeArea.addView(label(tmdbError ?: "No cast information available for this episode.",14f,secondary));return
  }
  val scroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false}
  val rail=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  cast.forEach{person->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(dp(104),-2).apply{rightMargin=dp(10)}}
   val photo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,10f);contentDescription=person.name}
   if(person.profileUrl!=null) photo.load(person.profileUrl){crossfade(true);allowHardware(false)}
   card.addView(photo,LinearLayout.LayoutParams(dp(104),dp(124)))
   card.addView(label(person.name,13f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(7),0,0)})
   if(person.character.isNotBlank()) card.addView(label(person.character,11f,secondary).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(3),0,0)})
   rail.addView(card)
  }
  scroller.addView(rail);episodeArea.addView(scroller,LinearLayout.LayoutParams(-1,-2))
  episodeArea.addView(label("Data provided by TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(14),0,0)})
 }

 private fun renderRelationsTab(){
  episodeArea.addView(label("Related titles",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(12)})
  if(tmdbLoading){episodeArea.addView(label("Finding related titles…",14f,secondary));return}
  val items=tmdbBundle?.relations.orEmpty()
  if(items.isEmpty()){episodeArea.addView(label(tmdbError ?: "No related titles available.",14f,secondary));return}
  val scroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false}
  val rail=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  items.forEach{item->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(dp(112),-2).apply{rightMargin=dp(12)}}
   val poster=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,10f);contentDescription=item.title}
   if(item.posterUrl!=null) poster.load(item.posterUrl){crossfade(true);allowHardware(false)}
   card.addView(poster,LinearLayout.LayoutParams(dp(112),dp(164)))
   card.addView(label(item.title,12f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(7),0,0)})
   rail.addView(card)
  }
  scroller.addView(rail);episodeArea.addView(scroller,LinearLayout.LayoutParams(-1,-2))
  episodeArea.addView(label("Recommendations and similar titles · TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(14),0,0)})
 }

 private fun renderDetailsTab(){
  episodeArea.addView(label("Information",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(10)})
  if(tmdbLoading){episodeArea.addView(label("Loading title details…",14f,secondary));return}
  val details=tmdbBundle?.details
  if(details==null){episodeArea.addView(label(tmdbError ?: "TMDB details are not available.",14f,secondary));return}
  val genres=details.optJSONArray("genres")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
  val studios=details.optJSONArray("production_companies")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.take(5).joinToString(" · ")}.orEmpty()
  val networks=details.optJSONArray("networks")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.take(4).joinToString(" · ")}.orEmpty()
  val date=details.optString("first_air_date").takeIf{it.isNotBlank()}?:details.optString("release_date")
  val rating=if(details.has("vote_average"))String.format("%.1f / 10",details.optDouble("vote_average")) else ""
  val language=details.optString("original_language").uppercase()
  val status=details.optString("status")
  val runtime=details.optInt("runtime").takeIf{it>0}?.let{"$it min"}?:""
  val rows=listOf("Genres" to genres,"Status" to status,"Release date" to date,"Rating" to rating,"Original language" to language,"Studios" to studios,"Networks" to networks,"Runtime" to runtime)
  rows.filter{it.second.isNotBlank()}.forEach{(key,value)->
   val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(10),0,dp(10))}
   row.addView(label(key.uppercase(),11f,0xFF777782.toInt(),true))
   row.addView(label(value,14f,Color.WHITE).apply{setPadding(0,dp(5),0,0)})
   episodeArea.addView(row,LinearLayout.LayoutParams(-1,-2))
   episodeArea.addView(View(this).apply{setBackgroundColor(0x22FFFFFF)},LinearLayout.LayoutParams(-1,dp(1)))
  }
  episodeArea.addView(label("Metadata provided by TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(12),0,0)})
 }

 private fun loadTmdbDetails(){
  tmdbLoading=true;tmdbError=null;renderTab()
  lifecycleScope.launch{
   try{
    val result=withContext(Dispatchers.IO){
     TmdbDetailRepository().load(title(),data.optString("mediaType"),data.optString("id"),selectedSeason,selectedEpisode)
    }
    tmdbBundle=result
   }catch(error:Exception){tmdbError=error.message ?: "TMDB request failed"}
   tmdbLoading=false
   renderTab()
  }
 }

}
