# Images

`logo.png` is a simple 256x256 brand mark (the same white "S" on a dark rounded square that the `.brand-icon` CSS draws in the page headers). It is included because the project structure lists it; the pages themselves still draw the mark with CSS, so replacing this file with your own logo changes nothing until you reference it from the JSP fragments.

Product images are optional URLs stored per row in `products.image_url` (products with no URL show a placeholder icon instead of a broken image). Product photos, if you host them locally instead of by URL, belong in `products/`.
