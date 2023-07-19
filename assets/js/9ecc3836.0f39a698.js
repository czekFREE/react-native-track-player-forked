"use strict";(self.webpackChunkdocs=self.webpackChunkdocs||[]).push([[4880],{3905:function(e,r,n){n.d(r,{Zo:function(){return s},kt:function(){return m}});var t=n(7294);function o(e,r,n){return r in e?Object.defineProperty(e,r,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[r]=n,e}function a(e,r){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var t=Object.getOwnPropertySymbols(e);r&&(t=t.filter((function(r){return Object.getOwnPropertyDescriptor(e,r).enumerable}))),n.push.apply(n,t)}return n}function i(e){for(var r=1;r<arguments.length;r++){var n=null!=arguments[r]?arguments[r]:{};r%2?a(Object(n),!0).forEach((function(r){o(e,r,n[r])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(r){Object.defineProperty(e,r,Object.getOwnPropertyDescriptor(n,r))}))}return e}function l(e,r){if(null==e)return{};var n,t,o=function(e,r){if(null==e)return{};var n,t,o={},a=Object.keys(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||(o[n]=e[n]);return o}(e,r);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var p=t.createContext({}),d=function(e){var r=t.useContext(p),n=r;return e&&(n="function"==typeof e?e(r):i(i({},r),e)),n},s=function(e){var r=d(e.components);return t.createElement(p.Provider,{value:r},e.children)},u={inlineCode:"code",wrapper:function(e){var r=e.children;return t.createElement(t.Fragment,{},r)}},c=t.forwardRef((function(e,r){var n=e.components,o=e.mdxType,a=e.originalType,p=e.parentName,s=l(e,["components","mdxType","originalType","parentName"]),c=d(n),m=o,f=c["".concat(p,".").concat(m)]||c[m]||u[m]||a;return n?t.createElement(f,i(i({ref:r},s),{},{components:n})):t.createElement(f,i({ref:r},s))}));function m(e,r){var n=arguments,o=r&&r.mdxType;if("string"==typeof e||o){var a=n.length,i=new Array(a);i[0]=c;var l={};for(var p in r)hasOwnProperty.call(r,p)&&(l[p]=r[p]);l.originalType=e,l.mdxType="string"==typeof e?e:o,i[1]=l;for(var d=2;d<a;d++)i[d]=n[d];return t.createElement.apply(null,i)}return t.createElement.apply(null,n)}c.displayName="MDXCreateElement"},6605:function(e,r,n){n.r(r),n.d(r,{assets:function(){return s},contentTitle:function(){return p},default:function(){return m},frontMatter:function(){return l},metadata:function(){return d},toc:function(){return u}});var t=n(7462),o=n(3366),a=(n(7294),n(3905)),i=["components"],l={sidebar_position:99},p="Amazon FireOS Support",d={unversionedId:"guides/amazon-fire-support",id:"version-3.1/guides/amazon-fire-support",title:"Amazon FireOS Support",description:"Support for Android in react-native-track-player is built on top of the ExoPlayer media player library provided by Google. ExoPlayer does not officially support Amazon's FireOS fork of Android, because it does not pass Android CTS. ExoPlayer seems to work decently on FireOS 5, but it hardly works at all on FireOS 4.",source:"@site/versioned_docs/version-3.1/guides/amazon-fire-support.md",sourceDirName:"guides",slug:"/guides/amazon-fire-support",permalink:"/docs/3.1/guides/amazon-fire-support",editUrl:"https://github.com/doublesymmetry/react-native-track-player/tree/main/docs/versioned_docs/version-3.1/guides/amazon-fire-support.md",tags:[],version:"3.1",sidebarPosition:99,frontMatter:{sidebar_position:99},sidebar:"app",previous:{title:"Multitrack Progress",permalink:"/docs/3.1/guides/multitrack-progress"},next:{title:"Migrating from v1 to v2",permalink:"/docs/3.1/v2-migration"}},s={},u=[{value:"Setup",id:"setup",level:2},{value:"Edit <code>app/build.gradle</code>",id:"edit-appbuildgradle",level:3},{value:"Build Using Variants",id:"build-using-variants",level:3}],c={toc:u};function m(e){var r=e.components,n=(0,o.Z)(e,i);return(0,a.kt)("wrapper",(0,t.Z)({},c,n,{components:r,mdxType:"MDXLayout"}),(0,a.kt)("h1",{id:"amazon-fireos-support"},"Amazon FireOS Support"),(0,a.kt)("p",null,"Support for Android in ",(0,a.kt)("inlineCode",{parentName:"p"},"react-native-track-player")," is built on top of the ",(0,a.kt)("a",{parentName:"p",href:"https://github.com/google/ExoPlayer"},"ExoPlayer")," media player library provided by Google. ExoPlayer does not officially support Amazon's FireOS fork of Android, because it does not pass ",(0,a.kt)("a",{parentName:"p",href:"https://source.android.com/compatibility/cts"},"Android CTS"),". ExoPlayer seems to work decently on FireOS 5, but it hardly works at all on FireOS 4."),(0,a.kt)("p",null,"Thankfully, ",(0,a.kt)("a",{parentName:"p",href:"https://developer.amazon.com/docs/fire-tv/media-players.html#exoplayer"},"Amazon maintains")," a ",(0,a.kt)("a",{parentName:"p",href:"https://github.com/amzn/exoplayer-amazon-port"},"ported version of ExoPlayer")," that can be used as a direct replacement as long as matching versions are used."),(0,a.kt)("h2",{id:"setup"},"Setup"),(0,a.kt)("p",null,"In order to fully support FireOS, you will need to build separate APKs for Google and Amazon. This can be accomplised using gradle flavors."),(0,a.kt)("p",null,"You will need to choose a ExoPlayer version that has been ported by Amazon, and that is close enough to the version that ",(0,a.kt)("inlineCode",{parentName:"p"},"react-native-track-player")," currently uses, in order to compile. In this example we have chosen to use ",(0,a.kt)("inlineCode",{parentName:"p"},"2.9.0"),"."),(0,a.kt)("h3",{id:"edit-appbuildgradle"},"Edit ",(0,a.kt)("inlineCode",{parentName:"h3"},"app/build.gradle")),(0,a.kt)("p",null,"Add ",(0,a.kt)("inlineCode",{parentName:"p"},"productFlavors")," to your build file:"),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre"},'android {\n  flavorDimensions "store"\n  productFlavors {\n    google {\n      dimension "store"\n    }\n    amazon {\n      dimension "store"\n    }\n  }\n  ...\n}\n')),(0,a.kt)("p",null,"Override the exoplayer library, and version, by modifying the dependencies:"),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre"},"dependencies {\n  compile (project(':react-native-track-player')) {\n    exclude group: 'com.google.android.exoplayer'\n  }\n  googleImplementation 'com.google.android.exoplayer:exoplayer-core:2.10.1'\n  amazonImplementation 'com.amazon.android:exoplayer-core:2.10.1'\n  ...\n}\n")),(0,a.kt)("h3",{id:"build-using-variants"},"Build Using Variants"),(0,a.kt)("p",null,"To make builds using either Google or Amazon libraries, you will need to specify a build variant when you build."),(0,a.kt)("p",null,"Here are some examples of ",(0,a.kt)("inlineCode",{parentName:"p"},"react-native")," commands using the ",(0,a.kt)("inlineCode",{parentName:"p"},"--variant")," parameter that can be added as scripts in ",(0,a.kt)("inlineCode",{parentName:"p"},"package.json"),":"),(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre"},'"scripts": {\n  "android-google": "react-native run-android --variant=googleDebug",\n  "android-amazon": "react-native run-android --variant=amazonDebug",\n  "android-release-google": "react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle && react-native run-android --variant=googleRelease",\n  "android-release-amazon": "react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle && react-native run-android --variant=amazonRelease",\n  ...\n}\n')))}m.isMDXComponent=!0}}]);