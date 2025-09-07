// 示例用户脚本 - 可以在这里添加自定义功能
// 这个脚本会在每个页面加载完成后执行

// 页面加载完成后的回调函数
(function() {
    'use strict';
    
    // 添加一个全局函数，网页可以通过这个函数与Android原生代码交互
    window.AndroidApp = {
        // 获取当前页面信息
        getPageInfo: function() {
            return {
                title: document.title,
                url: window.location.href,
                timestamp: new Date().toISOString()
            };
        },
        
        // 高亮页面中的所有链接
        highlightLinks: function() {
            var links = document.getElementsByTagName('a');
            for (var i = 0; i < links.length; i++) {
                links[i].style.border = '2px solid #ff0000';
            }
        },
        
        // 滚动到页面顶部
        scrollToTop: function() {
            window.scrollTo(0, 0);
        },
        
        // 滚动到页面底部
        scrollToBottom: function() {
            window.scrollTo(0, document.body.scrollHeight);
        }
    };
    
    // 自动为所有链接添加点击事件监听器
    document.addEventListener('DOMContentLoaded', function() {
        var links = document.getElementsByTagName('a');
        for (var i = 0; i < links.length; i++) {
            links[i].addEventListener('click', function(e) {
                // 可以在这里添加自定义逻辑，比如记录点击事件
                console.log('Link clicked: ' + this.href);
            });
        }
    });
    
    // 输出一条日志表示脚本已加载
    console.log('Custom userscript loaded');
})();