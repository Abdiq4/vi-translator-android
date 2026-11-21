# chiaki-ng's library asks for libevent unless it is built as part of chiaki-ng's own Android app
# (CHIAKI_ENABLE_ANDROID, which also builds that app's audio code). Android doesn't use it: an
# empty libevent stands in.
set(libevent_FOUND TRUE)
set(LIBEVENT_INCLUDE_DIRS "")
set(LIBEVENT_LIBRARIES "")
