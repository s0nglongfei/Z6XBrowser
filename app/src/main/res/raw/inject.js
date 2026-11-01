(function(){
  // 给所有可交互元素加 tabindex，让方向键能依次聚焦
  var els = document.querySelectorAll('a,button,video,input,select,textarea,[onclick],[role="button"]');
  for (var i=0;i<els.length;i++){
    if(!els[i].hasAttribute('tabindex')){
      els[i].setAttribute('tabindex','0');
    }
  }
  // 默认聚焦第一个视频；没有则聚焦第一个链接
  var v = document.querySelector('video');
  if(v){ v.focus(); }
  else {
    var a = document.querySelector('[tabindex="0"]');
    if(a){ a.focus(); }
  }
})();
