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
 private lateinit var tabsContainer: LinearLayout
 private var selectedTab = "Episodes"
 private var aniListFormat = ""
 private var downloadButton: View? = null
 private fun isMovie(): Boolean = data.optString("mediaType") == "movie" || data.optString("format").equals("MOVIE", true) || aniListFormat.equals("MOVIE", true)
 private var saved = false
 private var notified = false
 private var expanded = false
 private var listMode = false
 private var newestFirst = true
 private var selectedSeason = 1
 private var selectedEpisode = 1
 private var tmdbLoading = false
 private var tmdbError: String? = null
 private var tmdbBundle: TmdbDetailBundle? = null
 private var metaView: TextView? = null
 private var overviewView: TextView? = null
 private var genresView: TextView? = null
 private var creatorsView: TextView? = null
 private var episodeCount = 0
 private var aniListDetailsLoaded = false
 private var aniListMalId: Int? = null
 private var aniListItem: JSONObject? = null
 private val bg = 0xFF0B0B0F.toInt()
 private val surface = 0xFF16161C.toInt()
 private val panel = 0xFF20212A.toInt()
 private val secondary = 0xFFA7A7B2.toInt()
 private val red = 0xFFFF4D57.toInt()
 private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 private fun title()=data.optString("title").takeIf{it.isNotBlank()&&it!="null"}?:"Devils' Crest"
 private fun detectSeasonNumber(vararg values:String):Int = values.firstNotNullOfOrNull { value ->
  Regex("(?i)(?:season|s)\\s*[-:#]?\\s*(\\d+)").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
   ?: Regex("(?i)(\\d+)(?:st|nd|rd|th)\\s+season").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
 } ?: 1
 private fun episodes()=tmdbBundle?.episodes?.size?.takeIf{it>0} ?: if(aniListDetailsLoaded) episodeCount else data.optInt("episodes",0).takeIf{it>0} ?: 0
 private fun meta():String {
  val year=data.optString("year").takeIf{it.isNotBlank()&&it!="null"}?:"2026"
  val status=data.optString("status").takeIf{it.isNotBlank()&&it!="null"}?:"Ongoing"
  val count=if(isMovie()) "Movie" else episodes().let{if(it>0) "$it Episodes" else "Episodes TBA"}
  return "$year  ·  $count  ·  $status"
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
  selectedSeason = detectSeasonNumber(data.optString("title"), data.optString("season")).coerceAtLeast(1)
  if(isMovie()) selectedTab = "Cast"
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
  metaView=label(meta(),15f,secondary)
  content.addView(metaView,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12);bottomMargin=dp(18)})
  content.addView(primaryButton("Play",R.drawable.detail_play,Color.WHITE,Color.BLACK){toast("Install a streaming source to play this title")})
  content.addView(gap(10))
  downloadButton=primaryButton(if(isMovie()) "Download" else "Download E1",R.drawable.detail_download,panel,Color.WHITE){toast("No download source available")}
  content.addView(downloadButton)
  val sourceRow=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  sourceRow.addView(image(R.drawable.detail_hub,18).apply{alpha=.55f})
  sourceRow.addView(label("No sources installed",13f,0xFF6E6E78.toInt()).apply{setPadding(dp(7),0,0,0)})
  content.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(34)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  val synopsis=data.optString("description").takeIf{it.isNotBlank()&&it!="null"}?:""
  val overview=label(synopsis,15.5f,secondary).apply{maxLines=3;ellipsize=TextUtils.TruncateAt.END}
  overviewView=overview
  content.addView(overview)
  content.addView(label("Read more",13f,red,true).apply{
   setPadding(0,dp(8),0,dp(12))
   setOnClickListener{expanded=!expanded;overview.maxLines=if(expanded)Int.MAX_VALUE else 3;overview.ellipsize=if(expanded)null else TextUtils.TruncateAt.END;text=if(expanded)"Read less" else "Read more"}
  })
  val genres=data.optString("genres").takeIf{it.isNotBlank()&&it!="null"}?:""
  genresView=label(if(genres.isBlank()) "" else "Genres:  $genres",13f,secondary)
  content.addView(genresView)
  creatorsView=label(data.optString("creators").takeIf{it.isNotBlank()&&it!="null"}?.let{"Creators:  $it"}.orEmpty(),13f,secondary).apply{setPadding(0,dp(6),0,0)}
  content.addView(creatorsView)
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
  tabsContainer=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  rebuildTabs()
  content.addView(tabsContainer)
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
 private fun rebuildTabs(){
  tabsContainer.removeAllViews()
  val visibleTabs = if(isMovie()) listOf("Cast","Relations","Details") else listOf("Episodes","Cast","Relations","Details")
  if(selectedTab !in visibleTabs) selectedTab = visibleTabs.first()
  visibleTabs.forEach{tab->
   val cell=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(22)}}
   cell.addView(label(tab,15f,if(tab==selectedTab)red else secondary,tab==selectedTab).apply{setPadding(0,dp(10),0,dp(10));setOnClickListener{selectedTab=tab;refreshTabs(tabsContainer);renderTab()}})
   val underline=View(this).apply{tag=tab;background=shape(red,2f);visibility=if(tab==selectedTab)View.VISIBLE else View.INVISIBLE}
   cell.addView(underline,LinearLayout.LayoutParams(dp(if(tab=="Episodes")58 else tab.length*8),dp(3)))
   tabsContainer.addView(cell)
  }
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
  val toggle=FrameLayout(this).apply{
   background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)}
   contentDescription=if(listMode) "Switch to thumbnail view" else "Switch to simple list"
   setOnClickListener{listMode=!listMode;renderTab()}
  }
  toggle.addView(image(if(listMode)R.drawable.detail_list_view else R.drawable.detail_grid_view,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(toggle,LinearLayout.LayoutParams(dp(36),dp(36)).apply{rightMargin=dp(8)})
  val more=FrameLayout(this).apply{
   background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(panel)}
   setOnClickListener{
    PopupMenu(this@DetailActivity,this).apply{
     val newest=menu.add("Newest first");val oldest=menu.add("Oldest first")
     newest.isCheckable=true;oldest.isCheckable=true;newest.isChecked=newestFirst;oldest.isChecked=!newestFirst
     setOnMenuItemClickListener{item->val next=item.title.toString()=="Newest first";if(newestFirst!=next){newestFirst=next;renderTab()};true}
    }.show()
   }
  }
  more.addView(image(R.drawable.detail_more,20),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
  heading.addView(more,LinearLayout.LayoutParams(dp(36),dp(36)))
  episodeArea.addView(heading,LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(8);bottomMargin=dp(10)})
  val aniStatus = aniListItem?.optString("status").orEmpty()
  if (aniListDetailsLoaded && (aniStatus == "NOT_YET_RELEASED" || episodeCount <= 0)) {
   val message = if (aniStatus == "NOT_YET_RELEASED") "Episodes will appear here when this anime starts airing."
                 else "Episode information is not available yet."
   episodeArea.addView(label(message,14f,secondary).apply{setPadding(0,dp(8),0,dp(12))})
   return
  }
  if(tmdbLoading && tmdbBundle==null){
   episodeArea.addView(label("Loading episode details…",14f,secondary).apply{setPadding(0,dp(8),0,dp(12))})
   return
  }
  if(tmdbBundle==null || tmdbBundle?.episodes.isNullOrEmpty()){
   val message=tmdbError ?: "TMDB has not returned episode metadata for this season."
   episodeArea.addView(label(message,14f,secondary).apply{setPadding(0,dp(8),0,dp(10))})
   episodeArea.addView(label("Tap to retry",13f,red,true).apply{setOnClickListener{loadTmdbDetails()}})
   return
  }
  val episodeMap=tmdbBundle!!.episodes.associateBy{it.number}
  val numbers=episodeMap.keys.sorted().let{if(newestFirst)it.reversed() else it}
  if(listMode){
   val grid=GridLayout(this).apply{columnCount=5;useDefaultMargins=false}
   numbers.forEachIndexed{index,n->
    val tile=TextView(this).apply{
     text=n.toString();textSize=15f;setTextColor(if(n==selectedEpisode)Color.WHITE else Color.WHITE)
     typeface=Typeface.create("sans-serif",Typeface.BOLD);gravity=Gravity.CENTER
     background=shape(if(n==selectedEpisode)0xFFE5093A.toInt() else panel,10f)
     setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}
     contentDescription="Episode $n"
    }
    val lp=GridLayout.LayoutParams(
     GridLayout.spec(index/5,1f),GridLayout.spec(index%5,1f)
    ).apply{width=0;height=dp(58);setMargins(dp(4),dp(5),dp(4),dp(5))}
    grid.addView(tile,lp)
   }
   episodeArea.addView(grid,LinearLayout.LayoutParams(-1,-2).apply{leftMargin=dp(12);rightMargin=dp(12)})
  }else{
   numbers.forEach{n->
    val ep=episodeMap[n]
    val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,0,dp(15))}
    val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    val thumbFrame=FrameLayout(this).apply{background=shape(panel,10f);clipToOutline=true;layoutParams=LinearLayout.LayoutParams(dp(124),dp(72));setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}}
    val thumb=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel);contentDescription=ep?.name ?: "Episode $n";ep?.stillUrl?.let{load(it){crossfade(true);allowHardware(false)}}}
    thumbFrame.addView(thumb,FrameLayout.LayoutParams(-1,-1))
    thumbFrame.addView(label(if(ep!=null&&ep.rating>0) "★ %.1f".format(ep.rating) else "★ —",10f,Color.WHITE,true).apply{background=shape(0xCC000000.toInt(),5f);setPadding(dp(5),dp(3),dp(5),dp(3))},FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{leftMargin=dp(5);topMargin=dp(5)})
    thumbFrame.addView(image(R.drawable.detail_play,16).apply{setColorFilter(Color.WHITE);alpha=.9f},FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER))
    top.addView(thumbFrame)
    val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,dp(4),0);layoutParams=LinearLayout.LayoutParams(0,-2,1f);setOnClickListener{selectedEpisode=n;renderTab();loadTmdbDetails()}}
    info.addView(label("$n. ${ep?.name?.takeIf{it.isNotBlank()} ?: "Episode $n"}",15f,Color.WHITE,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END})
    val meta=buildList{add(if((ep?.runtime ?: 0)>0) "${ep!!.runtime} min" else "Runtime unavailable");add(ep?.airDate?.takeIf{it.isNotBlank()} ?: "Air date unavailable")}.joinToString("  ·  ")
    info.addView(label(meta,12f,secondary).apply{setPadding(0,dp(6),0,0);maxLines=2;ellipsize=TextUtils.TruncateAt.END})
    top.addView(info)
    top.addView(ImageView(this).apply{setImageResource(R.drawable.detail_download);setColorFilter(Color.WHITE);scaleType=ImageView.ScaleType.FIT_CENTER;alpha=.9f;contentDescription="Download episode $n";setOnClickListener{toast("No download source available for episode $n")}},LinearLayout.LayoutParams(dp(24),dp(24)).apply{leftMargin=dp(5);rightMargin=dp(3)})
    card.addView(top)
    val synopsis=ep?.overview?.takeIf{it.isNotBlank()} ?: "Episode description unavailable."
    card.addView(label(synopsis,13f,secondary).apply{typeface=Typeface.create("sans-serif",Typeface.ITALIC);maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(0,dp(9),0,0)})
    episodeArea.addView(card,LinearLayout.LayoutParams(-1,-2))
   }
  }
 }
 private fun renderCastTab(){
  episodeArea.addView(label("Cast",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(14)})
  val aniCast=aniListCastPeople()
  val cast=aniCast.ifEmpty{tmdbBundle?.cast.orEmpty()}
  if(cast.isEmpty()&&tmdbLoading){episodeArea.addView(label("Loading cast information…",14f,secondary));return}
  if(cast.isEmpty()){episodeArea.addView(label(tmdbError?:"No cast information available for this title.",14f,secondary));return}
  val grid=GridLayout(this).apply{columnCount=3;useDefaultMargins=false}
  cast.forEachIndexed{index,person->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
   val photo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,12f);contentDescription=person.name}
   person.profileUrl?.let{photo.load(it){crossfade(true);allowHardware(false)}}
   card.addView(photo,LinearLayout.LayoutParams(-1,dp(112)))
   card.addView(label(person.name,13f,Color.WHITE,true).apply{gravity=Gravity.CENTER;maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(dp(2),dp(7),dp(2),0)},LinearLayout.LayoutParams(-1,-2))
   val actor=person.character.removePrefix("Japanese VA: ").takeIf{it.isNotBlank()}
   if(actor!=null)card.addView(label(actor,11f,secondary).apply{gravity=Gravity.CENTER;maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(dp(2),dp(3),dp(2),0)},LinearLayout.LayoutParams(-1,-2))
   grid.addView(card,GridLayout.LayoutParams(GridLayout.spec(index/3,1f),GridLayout.spec(index%3,1f)).apply{width=0;height=-2;setMargins(dp(4),dp(2),dp(4),dp(26))})
  }
  episodeArea.addView(grid,LinearLayout.LayoutParams(-1,-2))
 }
 private fun aniListCastPeople(): List<TmdbPerson> {
  val edges = aniListItem?.optJSONObject("characters")?.optJSONArray("edges") ?: return emptyList()
  return (0 until edges.length()).mapNotNull { index ->
   val edge = edges.optJSONObject(index) ?: return@mapNotNull null
   val character = edge.optJSONObject("node") ?: return@mapNotNull null
   val characterName = character.optJSONObject("name")?.optString("full").orEmpty()
   if (characterName.isBlank()) return@mapNotNull null
   val characterImage = character.optJSONObject("image")?.optString("large")
   val voiceActor = edge.optJSONArray("voiceActors")?.optJSONObject(0)
   val voiceActorName = voiceActor?.optJSONObject("name")?.optString("full").orEmpty()
   val image = characterImage?.takeIf { it.startsWith("http") }
       ?: voiceActor?.optJSONObject("image")?.optString("large")?.takeIf { it.startsWith("http") }
   TmdbPerson(character.optInt("id"), characterName,
       voiceActorName.takeIf { it.isNotBlank() }?.let { "Japanese VA: $it" } ?: edge.optString("role"),
       image)
  }
 }

 private fun renderRelationsTab(){
  episodeArea.addView(label("Relations",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(14)})
  val aniRelations=aniListRelatedTitles()
  val items=aniRelations.ifEmpty{tmdbBundle?.relations.orEmpty()}
  if(items.isEmpty()&&tmdbLoading){episodeArea.addView(label("Finding related titles…",14f,secondary));return}
  if(items.isEmpty()){episodeArea.addView(label(tmdbError?:"No related titles available.",14f,secondary));return}
  val grid=GridLayout(this).apply{columnCount=3;useDefaultMargins=false}
  items.forEachIndexed{index,item->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   val poster=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(panel,10f);contentDescription=item.title}
   item.posterUrl?.let{poster.load(it){crossfade(true);allowHardware(false)}}
   card.addView(poster,LinearLayout.LayoutParams(-1,dp(164)))
   card.addView(label("RECOMMENDED",10f,red,true).apply{setPadding(0,dp(7),0,dp(2))})
   card.addView(label(item.title,13f,Color.WHITE).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END})
   grid.addView(card,GridLayout.LayoutParams(GridLayout.spec(index/3,1f),GridLayout.spec(index%3,1f)).apply{width=0;height=-2;setMargins(dp(4),dp(2),dp(8),dp(28))})
  }
  episodeArea.addView(grid,LinearLayout.LayoutParams(-1,-2))
 }
 private fun aniListRelatedTitles(): List<TmdbRelated> {
  val edges = aniListItem?.optJSONObject("relations")?.optJSONArray("edges") ?: return emptyList()
  val seen = linkedSetOf<Int>()
  return (0 until edges.length()).mapNotNull { index ->
   val edge = edges.optJSONObject(index) ?: return@mapNotNull null
   val node = edge.optJSONObject("node") ?: return@mapNotNull null
   val id = node.optInt("id")
   if (id <= 0 || !seen.add(id)) return@mapNotNull null
   val titleNode = node.optJSONObject("title")
   val title = listOf("english","romaji","native").map { titleNode?.optString(it).orEmpty() }
       .firstOrNull { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
   val image = node.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("http") }
   TmdbRelated(id,title,"",image,"anime")
  }.take(30)
 }

 private fun renderDetailsTab(){
  episodeArea.addView(label("Details",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(14)})
  val ani=aniListItem
  if(ani==null){
   if(tmdbLoading){episodeArea.addView(label("Loading title details…",14f,secondary));return}
   val details=tmdbBundle?.details
   if(details==null){episodeArea.addView(label(tmdbError?:"Title details are not available.",14f,secondary));return}
   val genres=details.optJSONArray("genres")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
   val studios=details.optJSONArray("production_companies")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.take(5).joinToString(" · ")}.orEmpty()
   val date=details.optString("first_air_date").takeIf{it.isNotBlank()}?:details.optString("release_date")
   addDetailRows(listOf("Status" to details.optString("status"),"Release date" to date,"Score" to if(details.has("vote_average"))String.format("%.1f / 10",details.optDouble("vote_average")) else "","Original language" to details.optString("original_language").uppercase(),"Studio" to studios,"Runtime" to details.optInt("runtime").takeIf{it>0}?.let{"$it min"}.orEmpty()))
   if(genres.isNotBlank())addDetailChipSection("Genres",genres.split(" · "))
   episodeArea.addView(label("Synopsis",14f,secondary,true).apply{setPadding(0,dp(20),0,dp(8))})
   episodeArea.addView(label(details.optString("overview"),15f,secondary))
   episodeArea.addView(label("Metadata provided by TMDB",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(16),0,0)})
   return
  }
  val titleNode=ani.optJSONObject("title")
  fun datePart(key:String):String{val node=ani.optJSONObject(key)?:return "";val y=node.optInt("year");val m=node.optInt("month");val d=node.optInt("day");return if(y>0&&m>0&&d>0)"%02d/%02d/%04d".format(d,m,y) else ""}
  val score=ani.optDouble("averageScore",0.0).takeIf{it>0}?.let{String.format("%.1f / 10",it/10.0)}.orEmpty()
  val genres=ani.optJSONArray("genres")?.let{arr->(0 until arr.length()).map{arr.optString(it)}.filter{it.isNotBlank()}}.orEmpty()
  val studios=ani.optJSONObject("studios")?.optJSONArray("nodes")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
  val tags=ani.optJSONArray("tags")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}.filter{!it.optBoolean("isMediaSpoiler")}.mapNotNull{tag->val name=tag.optString("name").takeIf{it.isNotBlank()}?:return@mapNotNull null;name to tag.optInt("rank",0)}.sortedByDescending{it.second}.take(20)}.orEmpty()
  val native=titleNode?.optString("native").orEmpty()
  val synonyms=ani.optJSONArray("synonyms")?.let{arr->(0 until arr.length()).map{arr.optString(it)}.filter{it.isNotBlank()}.take(8).joinToString(" · ")}.orEmpty()
  val aired=listOf(datePart("startDate"),datePart("endDate")).filter{it.isNotBlank()}.joinToString(" — ")
  addDetailRows(listOf("Source" to "AniList","Status" to ani.optString("status"),"Year" to ani.optInt("seasonYear").takeIf{it>0}?.toString().orEmpty(),"Episodes" to ani.optInt("episodes").takeIf{it>0}?.toString().orEmpty(),"Studio" to studios,"Format" to ani.optString("format"),"Score" to score,"Popularity" to ani.optInt("popularity").takeIf{it>0}?.let{String.format("%,d",it)}.orEmpty(),"Favourites" to ani.optInt("favourites").takeIf{it>0}?.let{String.format("%,d",it)}.orEmpty(),"Source material" to ani.optString("source"),"Country" to ani.optString("countryOfOrigin"),"Aired" to aired,"Native title" to native,"Also known as" to synonyms))
  if(genres.isNotEmpty())addDetailChipSection("Genres",genres)
  if(tags.isNotEmpty())addDetailChipSection("Tags",tags.map{"${it.first}  ${it.second}%"})
  episodeArea.addView(label("Synopsis",14f,secondary,true).apply{setPadding(0,dp(22),0,dp(8))})
  episodeArea.addView(label(ani.optString("description").takeIf{it.isNotBlank()&&it!="null"}.orEmpty(),15f,secondary).apply{setLineSpacing(dp(3).toFloat(),1f)})
  episodeArea.addView(label("Metadata provided by AniList",11f,0xFF6E6E78.toInt()).apply{setPadding(0,dp(18),0,0)})
 }

 private fun addDetailRows(rows:List<Pair<String,String>>){
  rows.filter{it.second.isNotBlank()&&it.second!="null"}.forEach{(key,value)->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(0,dp(8),0,dp(8))}
   row.addView(label(key,13f,0xFF777782.toInt()),LinearLayout.LayoutParams(dp(92),-2))
   row.addView(label(value,14f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
   episodeArea.addView(row,LinearLayout.LayoutParams(-1,-2))
  }
 }

 private fun addDetailChipSection(heading:String,values:List<String>){
  episodeArea.addView(label(heading,14f,secondary).apply{setPadding(0,dp(18),0,dp(8))})
  val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  var current=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  var used=0
  val maxWidth=(resources.displayMetrics.widthPixels/resources.displayMetrics.density).toInt()-32
  values.forEach{value->
   val chip=label(value,12f,0xFFA7A7B2.toInt(),true).apply{background=shape(0xFF24242C.toInt(),8f);setPadding(dp(9),dp(6),dp(9),dp(6))}
   val width=(chip.paint.measureText(value)+dp(18)).toInt().coerceAtLeast(dp(48))
   if(used>0&&used+width+dp(7)>maxWidth){rows.addView(current,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)});current=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};used=0}
   current.addView(chip,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=dp(7)})
   used+=width+dp(7)
  }
  if(current.childCount>0)rows.addView(current,LinearLayout.LayoutParams(-1,-2))
  episodeArea.addView(rows,LinearLayout.LayoutParams(-1,-2))
 }
 private fun loadTmdbDetails(){
  if (tmdbLoading) return
  tmdbLoading=true
  tmdbError=null
  renderTab()
  lifecycleScope.launch {
   val isAniListTitle = data.optString("mediaType") in listOf("anime","manga","novel") ||
       (data.optString("mediaType").isBlank() && data.optString("id").toIntOrNull()!=null)
   try {
    // AniList is the primary source for anime. Render its metadata immediately;
    // do not wait for the slower TMDB multi-endpoint lookup before showing tabs.
    val detailData = withContext(Dispatchers.IO) { loadAniListDetailIfNeeded() }
    applyDetailMetadata(detailData)
    if (isAniListTitle) {
     tmdbLoading = false
     renderTab()
     val status = aniListItem?.optString("status").orEmpty()
     // Upcoming titles and AniList movies have no season episode list to resolve.
     if (aniListFormat.equals("MOVIE", true) || status == "NOT_YET_RELEASED" || episodeCount <= 0) {
      tmdbBundle = null
      tmdbError = null
      return@launch
     }
     // AniList already powers Cast, Relations and Details; only fetch TMDB
     // episode enrichment in the background for released/airing series.
     tmdbLoading = true
     if (selectedTab != "Episodes") renderTab()
    }
    val result = withContext(Dispatchers.IO) {
     val kind = data.optString("mediaType")
     if (kind=="anime" || kind=="manga" || kind=="novel" || kind.isBlank())
      TmdbDetailRepository().load(title(), if(aniListFormat.equals("MOVIE",true)) "movie" else "tv", "", selectedSeason, selectedEpisode)
     else TmdbDetailRepository().load(title(), kind, data.optString("id"), selectedSeason, selectedEpisode)
    }
    tmdbBundle = if (data.optString("mediaType") == "anime" && aniListMalId != null) {
     val enriched = withContext(Dispatchers.IO) {
      AnimeEpisodeMetadataRepository.enrich(result.episodes, aniListMalId, result.tmdbId)
     }
     result.copy(episodes = enriched)
    } else result
   } catch (error: Exception) {
    tmdbError = error.message ?: "Metadata request failed"
   } finally {
    tmdbLoading = false
    renderTab()
   }
  }
 }

 private fun loadAniListDetailIfNeeded():JSONObject?{
  val kind=data.optString("mediaType")
  val id=data.optString("id").toIntOrNull() ?: return null
  if(kind !in listOf("anime","manga","novel")) return null
  val query="query(\$id:Int!){Media(id:\$id){id idMal title{english romaji native} description(asHtml:false) episodes format seasonYear season status averageScore source genres studios{nodes{name}} staff{edges{role node{name{full}}}} characters(page:1,perPage:30,sort:ROLE){edges{role node{id name{full} image{large}} voiceActors(language:JAPANESE){id name{full} image{large}}}} relations{edges{relationType(version:2) node{id type format title{english romaji native} coverImage{large} startDate{year}}}} countryOfOrigin synonyms popularity favourites tags{name rank isMediaSpoiler} startDate{year month day} endDate{year month day}}}"
  val body=JSONObject().put("query",query).put("variables",JSONObject().put("id",id))
  val connection=java.net.URL("https://graphql.anilist.co").openConnection() as java.net.HttpURLConnection
  connection.requestMethod="POST";connection.connectTimeout=4500;connection.readTimeout=6500;connection.doOutput=true
  connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json")
  connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
  val status=connection.responseCode
  val stream=if(status in 200..299) connection.inputStream else connection.errorStream
  val raw=stream.bufferedReader().use{it.readText()};connection.disconnect()
  if(status !in 200..299) throw IllegalStateException("AniList HTTP $status")
  return JSONObject(raw).optJSONObject("data")?.optJSONObject("Media")
 }

 private fun applyDetailMetadata(item:JSONObject?){
  if(item==null) return
  aniListItem = item
  val names=item.optJSONObject("title")
  val correctTitle=listOf("english","romaji","native").map{names?.optString(it).orEmpty()}.firstOrNull{it.isNotBlank()&&it!="null"}
  if(!correctTitle.isNullOrBlank()) data.put("title",correctTitle)
  val description=item.optString("description").takeIf{it.isNotBlank()&&it!="null"}.orEmpty()
  val genres=item.optJSONArray("genres")?.let{arr->(0 until arr.length()).map{arr.optString(it)}.filter{it.isNotBlank()}.joinToString(" · ")}.orEmpty()
  val studios=item.optJSONObject("studios")?.optJSONArray("nodes")?.let{arr->(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("name")?.takeIf{n->n.isNotBlank()}}.joinToString(" · ")}.orEmpty()
  val staff=item.optJSONObject("staff")?.optJSONArray("edges")
  val creators=staff?.let{arr->(0 until arr.length()).mapNotNull{index->
   val edge=arr.optJSONObject(index)?:return@mapNotNull null
   val role=edge.optString("role").lowercase()
   if(role.contains("director")||role.contains("creator")||role.contains("series composition")) edge.optJSONObject("node")?.optJSONObject("name")?.optString("full")?.takeIf{n->n.isNotBlank()} else null
  }.distinct().take(5).joinToString(" · ")}.orEmpty()
  episodeCount=item.optInt("episodes",0).takeIf{it>0}?:0
  aniListMalId=item.optInt("idMal",0).takeIf{it>0}
  aniListFormat=item.optString("format")
  aniListDetailsLoaded=true
  if(isMovie()) {
   selectedTab="Cast"
   rebuildTabs()
   (downloadButton as? LinearLayout)?.let { row -> (row.getChildAt(1) as? TextView)?.text = "Download" }
  }
  if(item.optInt("seasonYear")>0) data.put("year",item.optInt("seasonYear").toString())
  if(item.optString("status").isNotBlank()) data.put("status",item.optString("status"))
  data.put("description",description).put("genres",genres).put("creators",creators)
  overviewView?.text=description
  genresView?.text=if(genres.isBlank()) "" else "Genres:  $genres"
  creatorsView?.text=if(creators.isBlank()) if(studios.isBlank()) "" else "Studios:  $studios" else "Creators:  $creators"
  metaView?.text=meta()
 }

}
