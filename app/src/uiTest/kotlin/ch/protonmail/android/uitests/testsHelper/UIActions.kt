/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonMail.
 *
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.uitests.testsHelper

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ListView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.close
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnHolderItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToHolder
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ch.protonmail.android.R
import ch.protonmail.android.uitests.robots.contacts.ContactsMatchers.withContactEmail
import ch.protonmail.android.uitests.robots.contacts.ContactsMatchers.withContactEmailInManageAddressesView
import ch.protonmail.android.uitests.robots.contacts.ContactsMatchers.withContactGroupName
import ch.protonmail.android.uitests.robots.manageaccounts.ManageAccountsMatchers.withAccountEmailInAccountManager
import ch.protonmail.android.uitests.testsHelper.StringUtils.stringFromResource
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.checkContactDoesNotExist
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.checkMessageDoesNotExist
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.clickOnChildWithId
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.performActionWithRetry
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.saveMessageSubject
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitForAdapterItemWithIdAndText
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilIntentMatcherFulfilled
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilMatcherFulfilled
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilRecyclerViewPopulated
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilViewAppears
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilViewIsGone
import ch.protonmail.android.uitests.testsHelper.UICustomViewActions.waitUntilViewIsNotDisplayed
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.anything
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matcher
import org.junit.Assert

fun ViewInteraction.click(): ViewInteraction =
    this.perform(ViewActions.click())

fun ViewInteraction.insert(text: String): ViewInteraction =
    this.perform(replaceText(text), closeSoftKeyboard())

fun ViewInteraction.type(text: String): ViewInteraction =
    this.perform(typeText(text), closeSoftKeyboard())

fun ViewInteraction.swipeViewDown(): ViewInteraction =
    this.perform(swipeDown())

object UIActions {

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    val allOf = AllOf()

    class AllOf {
        fun clickMatchedView(viewMatcher: Matcher<View>): ViewInteraction =
            onView(viewMatcher).perform(click())

        fun clickViewWithIdAndAncestorTag(@IdRes id: Int, ancestorTag: String): ViewInteraction =
            onView(allOf(withId(id), isDescendantOfA(withTagValue(`is`(ancestorTag)))))
                .perform(click())

        fun clickViewWithIdAndText(@IdRes id: Int, text: String): ViewInteraction =
            onView(allOf(withId(id), withText(text))).perform(click())

        fun clickVisibleViewWithId(@IdRes id: Int): ViewInteraction =
            onView(allOf(withId(id), withEffectiveVisibility(Visibility.VISIBLE))).perform(click())

        fun clickViewWithParentIdAndClass(@IdRes id: Int, clazz: Class<*>): ViewInteraction =
            onView(allOf(instanceOf(clazz), withParent(withId(id)))).perform(click())

        fun clickViewByClassAndParentClass(clazz: Class<*>, parentClazz: Class<*>): ViewInteraction =
            onView(allOf(instanceOf(clazz), withParent(instanceOf(parentClazz)))).perform(click())!!

        fun clickViewWithIdAndText(@IdRes id: Int, @StringRes stringRes: Int): ViewInteraction =
            onView(allOf(withId(id), withText(stringRes)))
                .check(matches(isDisplayed()))
                .perform(click())

        fun setTextIntoFieldWithIdAndAncestorTag(
            @IdRes id: Int,
            ancestorTag: String,
            text: String
        ): ViewInteraction =
            onView(allOf(withId(id), isDescendantOfA(withTagValue(`is`(ancestorTag)))))
                .perform(replaceText(text))

        fun setTextIntoFieldWithIdAndHint(
            @IdRes id: Int,
            @StringRes stringId: Int,
            text: String
        ): ViewInteraction =
            onView(allOf(withId(id), withHint(stringId))).perform(replaceText(text))

        fun setTextIntoFieldByIdAndParent(
            @IdRes id: Int,
            @IdRes ancestorId: Int,
            text: String
        ): ViewInteraction =
            onView(allOf(withId(id), withEffectiveVisibility(Visibility.VISIBLE), isDescendantOfA(withId(ancestorId))))
                .perform(replaceText(text))
    }

    val check = Check()

    class Check {
        fun viewWithIdAndTextIsDisplayed(@IdRes id: Int, text: String): ViewInteraction =
            onView(allOf(withId(id), withText(text))).check(matches(isDisplayed()))

        fun viewWithIdIsNotDisplayed(@IdRes id: Int): ViewInteraction =
            onView(withId(id)).check(matches(not(isDisplayed())))

        fun viewWithIdIsContainsText(@IdRes id: Int, text: String): ViewInteraction =
            onView(withId(id)).check(matches(withText(containsString(text))))

        fun viewWithIdAndTextDoesNotExist(@IdRes id: Int, text: String): ViewInteraction =
            onView(allOf(withId(id), withText(text))).check(doesNotExist())

        fun viewWithTextIsChecked(@StringRes textId: Int): ViewInteraction =
            onView(withText(textId)).check(matches(isChecked()))

        fun viewWithIdAndAncestorTagIsChecked(
            @IdRes id: Int,
            ancestorTag: String,
            state: Boolean
        ): ViewInteraction {
            return when (state) {
                true ->
                    onView(allOf(withId(id), isDescendantOfA(withTagValue(`is`(ancestorTag)))))
                        .check(matches(isChecked()))
                false ->
                    onView(allOf(withId(id), isDescendantOfA(withTagValue(`is`(ancestorTag)))))
                        .check(matches(isNotChecked()))
            }
        }

        fun viewWithIdIsDisplayed(@IdRes id: Int): ViewInteraction = onView(withId(id)).check(matches(isDisplayed()))

        fun viewWithTextIsDisplayed(text: String): ViewInteraction =
            onView(withText(text)).check(matches(isDisplayed()))

        fun viewWithTextDoesNotExist(@StringRes textId: Int): ViewInteraction =
            onView(withText(stringFromResource(textId))).check(doesNotExist())

        fun viewWithIdAndTextIsDisplayed(@IdRes id: Int, @StringRes text: Int): ViewInteraction =
            onView(allOf(withId(id), withText(text))).check(matches(isDisplayed()))

        fun alertDialogWithTextIsDisplayed(@StringRes textId: Int): ViewInteraction =
            onView(withText(textId)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    val contentDescription = ContentDescription()

    class ContentDescription {
        fun clickViewWithContentDescSubstring(contDesc: String) {
            onView(withContentDescription(containsString(contDesc))).perform(click())
        }
    }

    val hint = Hint()

    class Hint {
        fun insertTextIntoFieldWithHint(@IdRes hintText: Int, text: String) {
            onView(withHint(hintText)).perform(replaceText(text))
        }
    }

    val id = Id()

    class Id {
        fun clickViewWithId(@IdRes id: Int): ViewInteraction = onView(withId(id)).perform(click())

        fun insertTextIntoFieldWithId(@IdRes id: Int, text: String): ViewInteraction =
            onView(withId(id)).perform(replaceText(text), closeSoftKeyboard())

        fun insertTextInFieldWithIdAndPressImeAction(@IdRes id: Int, text: String): ViewInteraction =
            onView(withId(id)).check(matches(isDisplayed())).perform(replaceText(text), pressImeActionButton())

        fun openMenuDrawerWithId(@IdRes id: Int): ViewInteraction = onView(withId(id)).perform(close(), open())

        fun swipeLeftViewWithId(@IdRes id: Int): ViewInteraction = onView(withId(id)).perform(swipeLeft())

        fun swipeDownViewWithId(@IdRes id: Int): ViewInteraction = onView(withId(id)).perform(swipeDown())

        fun typeTextIntoFieldWithIdAndPressImeAction(@IdRes id: Int, text: String): ViewInteraction =
            onView(withId(id)).perform(click(), typeText(text), pressImeActionButton())

        fun typeTextIntoFieldWithId(@IdRes id: Int, text: String): ViewInteraction =
            onView(withId(id)).perform(click(), typeText(text), closeSoftKeyboard())
    }

    val listView = List()

    class List {
        fun clickListItemByPosition(position: Int) {
            onData(anything())
                .inAdapterView(instanceOf(ListView::class.java))
                .inRoot(isPlatformPopup())
                .atPosition(position)
                .perform(click())
        }
    }

    val recyclerView = Recycler()

    class Recycler {

        fun clickOnRecyclerViewMatchedItem(
            @IdRes recyclerViewId: Int,
            withMatcher: Matcher<RecyclerView.ViewHolder>
        ): ViewInteraction =
            onView(withId(recyclerViewId)).perform(actionOnHolderItem(withMatcher, click()))

        fun clickOnRecyclerViewMatchedItemWithRetry(
            @IdRes recyclerViewId: Int,
            withMatcher: Matcher<RecyclerView.ViewHolder>
        ): ViewInteraction =
            performActionWithRetry(onView(withId(recyclerViewId)), actionOnHolderItem(withMatcher, click()))

        fun clickContactItem(@IdRes recyclerViewId: Int, withEmail: String): ViewInteraction =
            onView(withId(recyclerViewId)).perform(actionOnHolderItem(withContactEmail(withEmail), click()))

        fun clickContactItemWithRetry(@IdRes recyclerViewId: Int, withEmail: String): ViewInteraction =
            performActionWithRetry(
                onView(withId(recyclerViewId)),
                actionOnHolderItem(withContactEmail(withEmail), click())
            )

        fun clickContactItemView(
            @IdRes recyclerViewId: Int,
            withEmail: String,
            @IdRes childViewId: Int
        ): ViewInteraction = onView(withId(recyclerViewId))
            .perform(actionOnHolderItem(withContactEmail(withEmail), clickOnChildWithId(childViewId)))

        fun clickContactsGroupItemView(
            @IdRes recyclerViewId: Int,
            withName: String,
            @IdRes childViewId: Int
        ): ViewInteraction = onView(withId(recyclerViewId))
            .perform(actionOnHolderItem(withContactGroupName(withName), clickOnChildWithId(childViewId)))

        fun checkDoesNotContainMessage(@IdRes recyclerViewId: Int, subject: String, date: String): ViewInteraction =
            onView(withId(recyclerViewId)).perform(checkMessageDoesNotExist(subject, date))

        fun checkDoesNotContainContact(@IdRes recyclerViewId: Int, name: String, email: String): ViewInteraction =
            onView(withId(recyclerViewId)).perform(checkContactDoesNotExist(name, email))

        fun clickContactsGroupItem(@IdRes recyclerViewId: Int, withName: String): ViewInteraction =
            onView(withId(recyclerViewId)).perform(actionOnHolderItem(withContactGroupName(withName), click()))

        fun selectContactsInManageAddresses(@IdRes recyclerViewId: Int, withEmail: String): ViewInteraction =
            onView(withId(recyclerViewId))
                .perform(actionOnHolderItem(withContactEmailInManageAddressesView(withEmail), click()))

        fun clickAccountManagerViewItem(
            @IdRes recyclerViewId: Int,
            email: String,
            @IdRes childViewId: Int
        ): ViewInteraction = onView(withId(recyclerViewId))
            .perform(actionOnHolderItem(withAccountEmailInAccountManager(email), clickOnChildWithId(childViewId)))

        fun clickOnRecyclerViewItemByPosition(@IdRes recyclerViewId: Int, position: Int): ViewInteraction =
            onView(withId(recyclerViewId)).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))

        fun longClickItemInRecyclerView(@IdRes recyclerViewId: Int, childPosition: Int): ViewInteraction =
            onView(withId(recyclerViewId))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(childPosition, longClick()))

        fun saveMessageSubjectAtPosition(
            @IdRes recyclerViewId: Int,
            position: Int,
            method: (String, String) -> Unit
        ): ViewInteraction = onView(withId(recyclerViewId)).perform(saveMessageSubject(position, method))

        fun scrollToRecyclerViewMatchedItem(
            @IdRes recyclerViewId: Int,
            withMatcher: Matcher<RecyclerView.ViewHolder>
        ): ViewInteraction =
            onView(withId(recyclerViewId)).perform(scrollToHolder(withMatcher))

        fun swipeItemLeftToRightOnPosition(@IdRes recyclerViewId: Int, childPosition: Int): ViewInteraction =
            onView(withId(recyclerViewId))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(childPosition, swipeRight()))

        fun swipeDownToRightOnPosition(@IdRes recyclerViewId: Int, childPosition: Int): ViewInteraction =
            onView(withId(recyclerViewId))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(childPosition, swipeDown()))

        fun swipeRightToLeftObjectWithIdAtPosition(@IdRes recyclerViewId: Int, childPosition: Int): ViewInteraction =
            onView(withId(recyclerViewId))
                .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(childPosition, swipeLeft()))

        fun waitForBeingPopulated(@IdRes recyclerViewId: Int): Recycler {
            waitUntilRecyclerViewPopulated(recyclerViewId)
            return this
        }

        fun waitForItemWithIdAndText(@IdRes recyclerViewId: Int, @IdRes viewId: Int, text: String): Recycler {
            waitForAdapterItemWithIdAndText(recyclerViewId, viewId, text)
            return this
        }
    }

    val system = System()

    class System {
        fun clickHamburgerOrUpButton(): ViewInteraction =
            allOf.clickViewWithParentIdAndClass(R.id.toolbar, AppCompatImageButton::class.java)

        fun clickHamburgerOrUpButtonInAnimatedToolbar(): ViewInteraction =
            allOf.clickViewWithParentIdAndClass(R.id.animToolbar, AppCompatImageButton::class.java)

        fun waitForMoreOptionsButton(): ViewInteraction =
            wait.forViewByViewInteraction(
                onView(
                    allOf(
                        instanceOf(AppCompatImageView::class.java),
                        withParent(instanceOf(ActionMenuView::class.java))
                    )
                )
            )

        fun clickMoreOptionsButton(): ViewInteraction =
            allOf.clickViewByClassAndParentClass(AppCompatImageView::class.java, ActionMenuView::class.java)

        fun clickNegativeDialogButton(): ViewInteraction = id.clickViewWithId(android.R.id.button2)

        fun clickPositiveDialogButton(): ViewInteraction = id.clickViewWithId(android.R.id.button1)

        fun clickPositiveButtonInDialogRoot(): ViewInteraction = id.clickViewWithId(android.R.id.button1)
    }

    val tag = Tag()

    class Tag {
        fun clickViewWithTag(@StringRes tagStringId: Int): ViewInteraction =
            onView(withTagValue(`is`(targetContext.resources.getString(tagStringId)))).perform(click())
    }

    val text = Text()

    class Text {
        fun clickViewWithText(@IdRes text: Int): ViewInteraction =
            onView(withText(text)).check(matches(isDisplayed())).perform(click())

        fun clickViewWithText(text: String): ViewInteraction =
            onView(withText(text)).check(matches(isDisplayed())).perform(click())
    }

    val wait = Wait()

    class Wait {

        fun forIntent(matcher: Matcher<Intent>) = waitUntilIntentMatcherFulfilled(matcher)

        fun forViewWithIdAndText(@IdRes id: Int, text: String): ViewInteraction =
            waitUntilViewAppears(onView(allOf(withId(id), withText(text))))

        fun forViewWithIdAndText(@IdRes id: Int, textId: Int, timeout: Long = 5000): ViewInteraction =
            waitUntilViewAppears(onView(allOf(withId(id), withText(textId))), timeout)

        fun forViewWithContentDescription(@StringRes textId: Int): ViewInteraction =
            waitUntilViewAppears(onView(withContentDescription(containsString(stringFromResource(textId)))))

        fun forViewWithText(@StringRes textId: Int): ViewInteraction =
            waitUntilViewAppears(onView(withText(stringFromResource(textId))))

        fun forViewWithText(text: String): ViewInteraction =
            waitUntilViewAppears(onView(withText(text)))

        fun forViewWithTextByUiAutomator(text: String) {
            Assert.assertTrue(device.wait(Until.hasObject(By.text(text)), 5000))
        }

        fun forViewWithId(@IdRes id: Int, timeout: Long = 10_000L): ViewInteraction =
            waitUntilViewAppears(onView(withId(id)), timeout)

        fun forViewWithTextAndParentId(@StringRes text: Int, @IdRes parentId: Int): ViewInteraction =
            waitUntilViewAppears(onView(allOf(withText(text), withParent(withId(parentId)))))

        fun forViewWithIdAndAncestorId(@IdRes id: Int, @IdRes parentId: Int): ViewInteraction =
            waitUntilViewAppears(onView(allOf(withId(id), isDescendantOfA(withId(parentId)))))

        fun forViewOfInstanceWithParentId(@IdRes id: Int, clazz: Class<*>, timeout: Long = 5000): ViewInteraction =
            waitUntilViewAppears(onView(allOf(instanceOf(clazz), withParent(withId(id)))), timeout)

        fun forViewWithTextAndParentId(text: String, @IdRes parentId: Int): ViewInteraction =
            waitUntilViewAppears(onView(allOf(withText(text), withParent(withId(parentId)))))

        fun forViewByViewInteraction(interaction: ViewInteraction): ViewInteraction =
            waitUntilViewAppears(interaction)

        fun untilViewWithIdEnabled(@IdRes id: Int): ViewInteraction =
            waitUntilMatcherFulfilled(onView(withId(id)), matches(isEnabled()))

        fun untilViewWithIdDisabled(@IdRes id: Int): ViewInteraction =
            waitUntilMatcherFulfilled(onView(withId(id)), matches(not(isEnabled())))

        fun untilViewWithIdIsGone(@IdRes id: Int): ViewInteraction =
            waitUntilViewIsGone(onView(withId(id)))

        fun untilViewWithIdIsNotShown(@IdRes id: Int): ViewInteraction =
            waitUntilViewIsNotDisplayed(onView(withId(id)))

        fun untilViewByViewInteractionIsGone(interaction: ViewInteraction): ViewInteraction =
            waitUntilViewIsGone(interaction)
    }
}
